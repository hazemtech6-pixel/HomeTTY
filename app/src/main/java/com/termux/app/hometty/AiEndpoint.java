package com.termux.app.hometty;
import java.net.URI;
/** Explicit AI endpoint; credentials cannot be redirected to another server. */
public final class AiEndpoint {
    public final URI base, chat;
    public AiEndpoint(String address,boolean debug) {
        URI uri=URI.create(address);
        String scheme=uri.getScheme(),host=uri.getHost();
        if (host==null || uri.getUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null || (uri.getPort()!=-1 && (uri.getPort()<1 || uri.getPort()>65535))) throw new IllegalArgumentException("Use a server URL without credentials, query or fragment");
        boolean local="localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || debug && "10.0.2.2".equals(host);
        if (!"https".equalsIgnoreCase(scheme) && !("http".equalsIgnoreCase(scheme) && local)) throw new IllegalArgumentException("Use HTTPS; HTTP is allowed only for a local server");
        String value=uri.normalize().toASCIIString(); if (!value.endsWith("/")) value+="/";
        base=URI.create(value); chat=base.resolve("api/chat");
    }
    public boolean encrypted() { return "https".equalsIgnoreCase(base.getScheme()); }
}
