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

# companion with model run

```scala
        val tokenizer = JavaTokenizer.fromFile("./embedding-model/tokenizer.json")
        With(tokenizer) {
          val ortEnv = OrtEnvironment.getEnvironment()
          With(ortEnv) {
            val ortSession = ortEnv.createSession("./embedding-model/static-similarity-mrl-multilingual-v1-model_quantized.onnx", new OrtSession.SessionOptions())
            With(ortSession) {
//              val encoding = tokenizer.encode("敢问路在何方？路在脚下。")

              // 从下面两个句子相似性的embedding结果对比来看，才0.6多点儿的分， 感觉模型不给力啊！ 千问0.6b的效果应该都比这个好。 (0.796120)
//              val encoding = tokenizer.encode("你从哪儿来？")
              val encoding = tokenizer.encode("你是哪里人？")
              val ids = OnnxTensor.createTensor(ortEnv, Array(encoding.getIds.map(_.toLong)))
              val attentionMasks = OnnxTensor.createTensor(ortEnv, Array(encoding.getAttentionMask.map(_.toLong)))
              val input = new util.HashMap[String, OnnxTensor]()
              input.put("input_ids", ids)
              input.put("attention_mask", attentionMasks)
              val result = ortSession.run(input)
              With(result) {
                val onnxValue = result.get(0)
                println(onnxValue.getInfo.toString)
                println(onnxValue.getType.toString)
                // 1. 先将结果正确地转换为它本来的类型：二维浮点数组
                val embeddingBatch = onnxValue.getValue.asInstanceOf[Array[Array[Float]]]

                // 2. 从这个批次结果中，取出第一个（也是唯一一个）元素的 embedding 向量
                val embedding = embeddingBatch(0)

                // 现在 `embedding` 的类型就是 Array[Float] 了，可以正常使用
                println(s"Embedding size: ${embedding.length}") // 应该会输出 1024
                println(util.Arrays.asList(embedding: _*))
              }
            }
          }
        }
```



