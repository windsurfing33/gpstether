package com.gpstether.service;

import com.gpstether.service.ITetherServiceCallback;
/**
 */
interface ITetherService {
    /**
     * Often you want to allow a service to call back to its clients.
     * This shows how to do so, by registering a callback interface with
     * the service.
     */
    void registerCallback(ITetherServiceCallback cb);
    
    /**
     * Remove a previously registered callback interface.
     */
    void unregisterCallback(ITetherServiceCallback cb);
}