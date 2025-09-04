# Intro

This is a java wrapper to hugging face tokenizer(rust), currently, only macos dylib included in the distribution jar. (for my own use to static embedding model -> [https://huggingface.co/sentence-transformers/static-similarity-mrl-multilingual-v1](https://huggingface.co/sentence-transformers/static-similarity-mrl-multilingual-v1))

# How to use?

```java
    // 确保你有一个 tokenizer.json 文件。
    // 你可以从 Hugging Face 下载一个，比如 BAAI/bge-large-en-v1.5 的
    String tokenizerPath = "$SOME_PATH_TO/tokenizer.json";

    try (JavaTokenizer tokenizer = JavaTokenizer.fromFile(tokenizerPath)) {
        System.out.println("Tokenizer loaded successfully!");

        String text = "我就是福强，墙不倒我不倒～";

        // 测试默认行为（添加特殊标记）
        try (JavaTokenizer.Encoding output = tokenizer.encode(text)) {
            System.out.println("Text: " + text);
            System.out.println("With special tokens (default):");
            System.out.println("  IDs: " + Arrays.toString(output.getIds()));
            System.out.println("  Attention Mask: " + Arrays.toString(output.getAttentionMask()));
        }

        // 测试不添加特殊标记
        try (JavaTokenizer.Encoding output = tokenizer.encode(text, false)) {
            System.out.println("Without special tokens:");
            System.out.println("  IDs: " + Arrays.toString(output.getIds()));
            System.out.println("  Attention Mask: " + Arrays.toString(output.getAttentionMask()));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
```



