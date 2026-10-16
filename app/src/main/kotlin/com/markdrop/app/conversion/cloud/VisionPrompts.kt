package com.markdrop.app.conversion.cloud

object VisionPrompts {

    const val SYSTEM_INSTRUCTION = """You are an expert document transcription assistant.
Your task is to transcribe the provided PDF page image into clean, pristine Markdown format.
Guidelines:
1. Output ONLY the extracted Markdown text. Do NOT include greetings, preamble, explanations, or code blocks wrapping the entire output (no ```markdown fences around the response).
2. Faithfully format headings using proper Markdown (# H1, ## H2, ### H3) based on typographic hierarchy.
3. Transcribe lists with proper bullet points (- or numbered 1.).
4. Format tables as strict GitHub Flavored Markdown (GFM) pipe tables (| Col 1 | Col 2 |).
5. For mathematical equations, use standard LaTeX notation ($...$ for inline, $$...$$ for block math).
6. If diagrams, charts, or images are present, insert a concise descriptive blockquote explaining the figure: > **Figure [Number/Title]:** [Description of diagram contents].
7. Preserve all textual content accurately without summarizing or altering words."""

    const val PAGE_PROMPT = "Transcribe this page image into clean Markdown following the system instructions."

    const val CONNECTION_TEST_PROMPT = "Respond with 'OK' if you can read this."
}
