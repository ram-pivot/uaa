package org.cloudfoundry.identity.uaa.ratelimiting.config;

import java.io.IOException;
import javax.annotation.PreDestroy;
import javax.validation.constraints.NotNull;

import org.cloudfoundry.identity.uaa.ratelimiting.core.config.exception.YamlRateLimitingConfigException;
import org.cloudfoundry.identity.uaa.ratelimiting.internal.common.LimiterFactorySupplierUpdatable;
import org.cloudfoundry.identity.uaa.ratelimiting.internal.common.RateLimitingFactoriesSupplierWithStatus;
import org.cloudfoundry.identity.uaa.ratelimiting.internal.limitertracking.LimiterManagerImpl;
import org.cloudfoundry.identity.uaa.ratelimiting.util.MillisTimeSupplier;

import static org.cloudfoundry.identity.uaa.ratelimiting.config.RateLimitingConfig.Fetcher;
import static org.cloudfoundry.identity.uaa.ratelimiting.config.RateLimitingConfig.LoaderLogger;

public class RateLimitingConfigLoader implements Runnable {
    public static final String YAML_FETCH_FAILED = "Fetch Failed";
    public static final String YAML_NULL = "null";
    public static final String YAML_EMPTY = "empty";
    public static final String YAML_NO_DATA = "no data";
    public static final String TYPE_PROPERTIES_PROBLEM = "unacceptable/incompatible TypeProperties: ";

    private final LoaderLogger logger;
    private final Fetcher fetcher;
    private final String dynamicUpdateURL;
    private final RateLimitingConfigMapper configMapper;
    private final LimiterFactorySupplierUpdatable supplierUpdatable;
    private final MillisTimeSupplier currentTimeSupplier;
    private final BindYaml<YamlConfigFileDTO> bindYaml;
    private RateLimitingFactoriesSupplierWithStatus current;
    private String lastYAML = ""; // Cached Data to compare against next read!
    private volatile boolean wereDying = false;
    private Thread backgroundThread;

    /**
     * Constructor
     *
     * @param logger       nullable, and if null means NO Rate Limiting
     * @param fetcher      nullable, and if null means NO Rate Limiting
     * @param configMapper nullable, and if null means NO Rate Limiting
     */
    public RateLimitingConfigLoader( LoaderLogger logger, Fetcher fetcher, String dynamicUpdateURL,
                                     RateLimitingConfigMapper configMapper, RateLimitingFactoriesSupplierWithStatus current ) {
        this( logger, fetcher, dynamicUpdateURL, configMapper, current, LimiterManagerImpl.SINGLETON.getInstance(), null, false );
    }

    @PreDestroy
    public void dying() {
        wereDying = true;
        if ( backgroundThread != null ) {
            backgroundThread.interrupt();
            supplierUpdatable.shutdownBackgroundProcessing();
        }
    }

    // package friendly and more params for Testing
    RateLimitingConfigLoader( LoaderLogger logger, Fetcher fetcher, String dynamicUpdateURL,
                              RateLimitingConfigMapper configMapper, RateLimitingFactoriesSupplierWithStatus current,
                              @NotNull LimiterFactorySupplierUpdatable supplierUpdatable,
                              MillisTimeSupplier currentTimeSupplier, boolean noBackgroundProcessor ) {
        this.logger = logger;
        this.fetcher = fetcher;
        this.dynamicUpdateURL = dynamicUpdateURL;
        this.configMapper = configMapper;
        this.supplierUpdatable = supplierUpdatable;
        this.currentTimeSupplier = MillisTimeSupplier.deNull( currentTimeSupplier );
        bindYaml = new BindYaml<>( YamlConfigFileDTO.class, dynamicUpdateURL );
        this.current = current;

        if ( fetcher != null ) { // Rate Limiting active
            if ( !noBackgroundProcessor ) {
                supplierUpdatable.startBackgroundProcessing();
                backgroundThread = new Thread( this );
                backgroundThread.setName( "TypesPropertiesLoaderProcess" );
                backgroundThread.setDaemon( true );
                backgroundThread.start();
            }
        }
    }

    // package friendly for testing
    String getLastYAML() {
        return lastYAML;
    }

    // package friendly for testing
    boolean checkForUpdatedProperties() {
        RateLimitingFactoriesSupplierWithStatus updated = null;
        try {
            String yamlString = loadYamlString();
            if ( !lastYAML.equals( yamlString ) ) { // check for change
                lastYAML = yamlString; // update last state to force wait for another change (minimize dup errors)
                YamlConfigFileDTO dto = parseFile( yamlString );
                updated = configMapper.map( current, dynamicUpdateURL, dto );
            }
        }
        catch ( Exception e ) {
            updated = current.updateError( e, currentTimeSupplier.now() );
        }
        if ( updated != null ) {
            current = updated;
            supplierUpdatable.update( updated );
            logger.logUpdate( "Update: " + updated.getStatusJson() );
            return true;
        }
        return false;
    }

    @SuppressWarnings("BusyWait")
    @Override
    public void run() {
        logger.logUpdate( "Polling Background thread started" );
        try {
            while ( !wereDying ) {
                try {
                    long nextRunTime = currentTimeSupplier.now() + 15000;
                    Thread.sleep( 2000 ); // check every 15 seconds (2 here & rest below)
                    checkForUpdatedProperties();
                    Thread.sleep( nextRunTime - currentTimeSupplier.now() );
                }
                catch ( InterruptedException e ) {
                    // As it is a Daemon, ignore InterruptedException and check if "wereDying"!
                }
                catch ( Exception e ) {
                    logger.logUnhandledError( e ); // Log everything else
                }
            }
        }
        catch ( Exception e ) { // shouldn't be possible!
            logger.logUnhandledError( e );
        }
        finally {
            logger.logUpdate( "Polling Background thread stopping" );
        }
    }

    // package friendly for testing
    String loadYamlString() {
        String origString;
        try {
            origString = fetcher.fetchYaml();
        }
        catch ( IOException e ) {
            throw new YamlRateLimitingConfigException( null, YAML_FETCH_FAILED, e );
        }
        if ( origString == null ) {
            throw new YamlRateLimitingConfigException( null, YAML_NULL );
        }
        String yamlString = origString.stripLeading();
        if ( yamlString.isEmpty() ) {
            throw new YamlRateLimitingConfigException( origString, YAML_EMPTY );
        }
        yamlString = BindYaml.removeLeadingEmptyDocuments( yamlString );
        if ( yamlString.isEmpty() ) {
            throw new YamlRateLimitingConfigException( origString, YAML_NO_DATA );
        }
        return origString;
    }

    private YamlConfigFileDTO parseFile( String yamlString ) {
        return bindYaml.bind( yamlString );
    }
}
