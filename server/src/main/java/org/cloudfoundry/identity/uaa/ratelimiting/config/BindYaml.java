package org.cloudfoundry.identity.uaa.ratelimiting.config;

import lombok.RequiredArgsConstructor;
import org.cloudfoundry.identity.uaa.ratelimiting.core.config.exception.YamlRateLimitingConfigException;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

@RequiredArgsConstructor
public class BindYaml {
    private final String sourcedFrom;

    public String removeLeadingEmptyDocuments( String yaml) {
        yaml = (yaml == null) ? "" : yaml.stripLeading();
        while (yaml.startsWith( "---" )) {
            yaml = yaml.substring( 3 ).stripLeading();
            if (yaml.startsWith( "{}" )) {
                yaml = yaml.substring( 2 ).stripLeading();
            }
        }
        return yaml;
    }

    public <T> T bind( Class<T> targetClass, String yaml ) {
        T target = null;
        if ( yaml != null ) {
            Yaml yamlParser = new Yaml( new Constructor( targetClass ) );
            try {
                target = yamlParser.load( yaml );
            }
            catch ( RuntimeException e ) {
                String message = e.getMessage();
                String cleaned = message.replace( targetClass.getName(), targetClass.getSimpleName() );
                throw new YamlRateLimitingConfigException( yaml, sourcedFrom + ": " + cleaned );
            }
        }
        return target;
    }
}
