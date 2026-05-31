// src/main/aidl/com/jabby/vlm/service/IVlmService.aidl
package com.jabby.vlm.service;

import com.jabby.vlm.service.IVlmCallback;

interface IVlmService {
    /**
     * Select compute backend before loadModel().
     * Accepted: "cpu" (default), "gpu" (OpenCL), "htp" (Hexagon NPU).
     */
    void setBackend(String backend);

    /**
     * Cap the number of visual tokens the vision encoder produces per image.
     * Must be called BEFORE loadMmproj() — the cap is read into the
     * mtmd_context_params at init time. Pass 0 to use the mmproj's default
     * (read from GGUF metadata).
     *
     * In our personsense-bench data, max=72 on Qwen3-VL 2B drops S25 TTFT to
     * ~2.8 s (vs ~14 s at the natural ~300 visual tokens) with mAP 0.48.
     */
    void setImageMaxTokens(int maxTokens);

    /**
     * Load the text backbone GGUF model.
     * Blocks until loading completes.
     * @param modelPath absolute path to the Qwen3-VL GGUF file
     * @return true if successful
     */
    boolean loadModel(String modelPath);

    /**
     * Load the vision projector (mmproj) GGUF.
     * Must be called after loadModel().
     * @param mmprojPath absolute path to the mmproj GGUF file
     * @return true if successful
     */
    boolean loadMmproj(String mmprojPath);

    /**
     * Set the system prompt.
     * @return true if successful
     */
    boolean setSystemPrompt(String prompt);

    /**
     * Describe an image with an optional text prompt.
     * Non-blocking — tokens arrive via IVlmCallback.onToken().
     *
     * @param imageBytes JPEG or PNG bytes of the image to describe
     * @param textPrompt user instruction (e.g. "Describe this image in detail.")
     * @param maxTokens  maximum tokens to generate
     * @param callback   receiver for streaming tokens
     */
    void describeImage(
        in byte[] imageBytes,
        String textPrompt,
        int maxTokens,
        IVlmCallback callback
    );

    /**
     * Describe multiple images with an optional text prompt.
     * Non-blocking — tokens arrive via IVlmCallback.onToken().
     *
     * Images are passed as a single concatenated byte array + an int array of
     * individual image sizes (AIDL does not support byte[][]).
     *
     * @param imageData   all images concatenated into one byte[]
     * @param imageSizes  size of each image in imageData
     * @param textPrompt  user instruction
     * @param maxTokens   maximum tokens to generate
     * @param callback    receiver for streaming tokens
     */
    void describeImages(
        in byte[] imageData,
        in int[] imageSizes,
        String textPrompt,
        int maxTokens,
        IVlmCallback callback
    );

    /**
     * Cancel ongoing generation.
     */
    void cancelGeneration();

    /**
     * Unload the current model and mmproj to free memory.
     */
    void unloadModel();

    /**
     * Check if both model and mmproj are loaded and ready.
     */
    boolean isReady();

    /**
     * Get current state as string (for debugging).
     * Returns: "Uninitialized", "Initialized", "LoadingModel",
     *          "ModelReady", "Generating", "Error", etc.
     */
    String getState();
}
