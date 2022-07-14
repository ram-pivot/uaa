package org.cloudfoundry.identity.uaa.ratelimiting.core.http;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.cloudfoundry.identity.uaa.ratelimiting.util.LazyEnumerationToList;
import org.cloudfoundry.identity.uaa.ratelimiting.util.StringUtils;

public class RequestInfoImpl implements RequestInfo {
    private final Map<String, LazyEnumerationToList<String>> headerLists = new HashMap<>();
    private final HttpServletRequest request;
    private final LazyEnumerationToList<String> headerNames;

    private RequestInfoImpl( HttpServletRequest request ) {
        this.request = request;
        headerNames = new LazyEnumerationToList<>( this.request::getHeaderNames );
    }

    public static RequestInfo from( HttpServletRequest request ) {
        return (request != null) ? new RequestInfoImpl( request ) : new NullObjectRequestInfo();
    }

    @Override
    public String getServletPath() {
        return request.getServletPath();
    }

    @Override
    public String getAuthorizationHeader() {
        return header( "Authorization" );
    }

    @Override
    public String getClientIP() {
        String value = header( "X-Client-IP" );
        if ( value == null ) {
            value = header( "X-Real-IP" );
        }
        if ( value == null ) {
            value = header( "X-Forwarded-For" ); // Added by the GoRouter
            if ( value != null ) {
                int at = value.indexOf( ',' );
                if ( at != -1 ) {
                    value = StringUtils.normalizeToNull( value.substring( 0, at ) );
                }
            }
        }
        return (value != null) ? value : StringUtils.normalizeToNull( getRemoteAddr() );
    }

    public boolean hasHeaderNames() {
        return headerNames.hasValue();
    }

    public List<String> getHeaderNames() {
        return headerNames.get();
    }

    @SuppressWarnings("unused")
    public boolean hasHeaders( String name ) {
        return headersFor( name ).hasValue();
    }

    public List<String> getHeaders( String name ) {
        return headersFor( name ).get();
    }

    public String getHeader( String name ) {
        return request.getHeader( name );
    }

    public Principal getPrincipal() {
        return request.getUserPrincipal();
    }

    public String getAuthType() {
        return request.getAuthType();
    }

    public String getContextPath() {
        return request.getContextPath();
    }

    public String getMethod() {
        return request.getMethod();
    }

    public String getRequestURI() {
        return request.getRequestURI();
    }

    public String getRemoteAddr() {
        return request.getRemoteAddr();
    }

    public String getRemoteUser() {
        return request.getRemoteUser();
    }

    @Override
    public String toString() {
        return "RequestInfo{" + "authType='" + getAuthType() + '\'' +
               ", contextPath='" + getContextPath() + '\'' +
               ", method='" + getMethod() + '\'' +
               ", requestURI='" + getRequestURI() + '\'' +
               ", remoteAddr='" + getRemoteAddr() + '\'' +
               ", remoteUser='" + getRemoteUser() + '\'' +
               ", servletPath='" + getServletPath() + '\'' +
               ", principal=" + getPrincipal() +
               ", hasHeaderNames=" + hasHeaderNames() +
               ", headerNames=" + getHeaderNames() +
               ", header:Authorization=" + getHeader( "Authorization" ) +
               '}';
    }

    private String header( String name ) {
        return StringUtils.normalizeToNull( getHeader( name ) );
    }

    private LazyEnumerationToList<String> headersFor( String name ) {
        if ( name != null ) {
            name = name.toLowerCase();
        }
        LazyEnumerationToList<String> le2l = headerLists.get( name );
        if ( le2l == null ) {
            le2l = new LazyEnumerationToList<>( request.getHeaders( name ) );
            headerLists.put( name, le2l );
        }
        return le2l;
    }

    private static class NullObjectRequestInfo implements RequestInfo {
        @Override
        public String getServletPath() {
            return "No HttpServletRequest to Proxy!";
        }

        @Override
        public String getAuthorizationHeader() {
            return null;
        }

        @Override
        public String getClientIP() {
            return null;
        }
    }
}