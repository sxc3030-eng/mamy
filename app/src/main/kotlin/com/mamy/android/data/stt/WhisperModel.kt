package com.mamy.android.data.stt

data class WhisperModel(
    val id: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val expectedBytes: Long,
) {
    companion object {
        val TINY = WhisperModel(
            id = "tiny",
            fileName = "ggml-tiny.bin",
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            // sha256 from huggingface for ggml-tiny.bin (whisper.cpp v1.7.x)
            sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
            expectedBytes = 77_691_713L,
        )
    }
}
