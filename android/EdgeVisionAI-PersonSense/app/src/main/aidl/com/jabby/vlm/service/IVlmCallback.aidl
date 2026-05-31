// src/main/aidl/com/jabby/vlm/service/IVlmCallback.aidl
package com.jabby.vlm.service;

/**
 * Callback for receiving streaming VLM responses.
 * Implemented by the client, called by the service.
 */
oneway interface IVlmCallback {
    /** Called for each generated token */
    void onToken(String token);

    /** Called when generation completes normally */
    void onComplete();

    /** Called on error */
    void onError(String errorMessage);
}
