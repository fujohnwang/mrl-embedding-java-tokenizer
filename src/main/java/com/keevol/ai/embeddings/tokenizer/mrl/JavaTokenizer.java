package com.keevol.ai.embeddings.tokenizer.mrl;

/**
 * <pre>
 * :::    ::: :::::::::: :::::::::: :::     :::  ::::::::  :::
 * :+:   :+:  :+:        :+:        :+:     :+: :+:    :+: :+:
 * +:+  +:+   +:+        +:+        +:+     +:+ +:+    +:+ +:+
 * +#++:++    +#++:++#   +#++:++#   +#+     +:+ +#+    +:+ +#+
 * +#+  +#+   +#+        +#+         +#+   +#+  +#+    +#+ +#+
 * #+#   #+#  #+#        #+#          #+#+#+#   #+#    #+# #+#
 *  * </pre>
 * <p>
 * KEEp eVOLution!
 * <p>
 *
 * @author fq@keevol.cn
 * @since 2017.5.12
 * <p>
 * Copyright 2017 © 杭州福强科技有限公司版权所有 (<a href="https://www.keevol.cn">keevol.cn</a>)
 */

import com.keevol.goodies.ClasspathResources;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Arrays;

public class JavaTokenizer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(JavaTokenizer.class);

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOADER;

    // --- Method Handles for the Rust functions ---
    private static final MethodHandle tokenizer_from_file;
    private static final MethodHandle tokenizer_free;
    private static final MethodHandle tokenizer_encode;
    private static final MethodHandle encoding_free;
    private static final MethodHandle encoding_get_ids;
    private static final MethodHandle encoding_get_attention_mask;
    private static final MethodHandle encoding_free_array;

    private static final String cacheLibsPath = ".keevol/cache/libs/";
    private static final File libsCacheDir = new File(System.getProperty("user.home"), cacheLibsPath);

    // --- Static initializer to load the library and look up symbols ---
    static {
        try {
            logger.info("mkdirs if necessary: {}", libsCacheDir.getAbsolutePath());
            FileUtils.forceMkdir(libsCacheDir);
            try {
                ClasspathResources.copyToLocalFS("/libs/libjava_tokenizer_bridge-macos-arm64.dylib", libsCacheDir);
                ClasspathResources.copyToLocalFS("/libs/libjava_tokenizer_bridge-macos-x64.dylib", libsCacheDir);
            } catch (IOException e) {
                logger.warn("dynamic lib exists, ignore copy.");
            }
        } catch (IOException e) {
            logger.warn("fails to extract dynamic libs to local cache dir: {}", e.toString());
        }


        // 自动检测平台并加载对应的动态库
        String libPath = detectAndLoadLibrary();
        System.load(libPath);
        LOADER = SymbolLookup.loaderLookup();

        // --- Define function signatures and link them ---
        tokenizer_from_file = LINKER.downcallHandle(
                LOADER.find("tokenizer_from_file").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        tokenizer_free = LINKER.downcallHandle(
                LOADER.find("tokenizer_free").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
        tokenizer_encode = LINKER.downcallHandle(
                LOADER.find("tokenizer_encode").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN)
        );
        encoding_free = LINKER.downcallHandle(
                LOADER.find("encoding_free").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );
        encoding_get_ids = LINKER.downcallHandle(
                LOADER.find("encoding_get_ids").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        encoding_get_attention_mask = LINKER.downcallHandle(
                LOADER.find("encoding_get_attention_mask").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );
        encoding_free_array = LINKER.downcallHandle(
                LOADER.find("encoding_free_array").orElseThrow(),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
        );
    }

    private final MemorySegment tokenizerPtr;

    /**
     * 自动检测平台并返回对应的动态库路径
     */
    private static String detectAndLoadLibrary() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String libName;
        String extension;

        if (os.contains("win")) {
            libName = "libjava_tokenizer_bridge-windows-x64";
            extension = ".dll";
        } else if (os.contains("mac")) {
            if (arch.contains("aarch64") || arch.contains("arm")) {
                libName = "libjava_tokenizer_bridge-macos-arm64";
            } else {
                libName = "libjava_tokenizer_bridge-macos-x64";
            }
            extension = ".dylib";
        } else if (os.contains("nix") || os.contains("nux")) {
            if (arch.contains("aarch64") || arch.contains("arm")) {
                libName = "libjava_tokenizer_bridge-linux-arm64";
            } else {
                // 尝试多个 Linux 变体
                String[] linuxVariants = {
                        "libjava_tokenizer_bridge-linux-x64",
                        "libjava_tokenizer_bridge-linux-x64-musl"
                };

                for (String variant : linuxVariants) {
                    String[] possiblePaths = {
                            "libs/" + variant + ".so",
                            variant + ".so",
                            "libs/libjava_tokenizer_bridge.so"
                    };

                    for (String path : possiblePaths) {
                        File libFile = new File(path);
                        if (libFile.exists()) {
                            System.out.println("Found library: " + libFile.getAbsolutePath());
                            return libFile.getAbsolutePath();
                        }
                    }
                }

                libName = "libjava_tokenizer_bridge-linux-x64";
            }
            extension = ".so";
        } else {
            throw new UnsupportedOperationException("Unsupported platform: " + os + " " + arch);
        }

        // 尝试多个可能的路径
        String[] possiblePaths = {
                // 2. 当前目录
                libName + extension,
                "libs/" + libName + extension,
                new File(System.getProperty("user.home"), cacheLibsPath + libName + extension).getAbsolutePath(),
                // 绝对路径（向后兼容）
                new File(System.getProperty("user.home"), cacheLibsPath + "libjava_tokenizer_bridge" + extension).getAbsolutePath()
        };

        for (String path : possiblePaths) {
            File libFile = new File(path);
            if (libFile.exists()) {
                System.out.println("Found library: " + libFile.getAbsolutePath());
                return libFile.getAbsolutePath();
            }
        }

        throw new RuntimeException("Could not find native library for platform: " + os + " " + arch +
                "\nTried paths: " + String.join(", ", possiblePaths));
    }

    private JavaTokenizer(MemorySegment tokenizerPtr) {
        if (tokenizerPtr.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("Failed to load tokenizer from file.");
        }
        this.tokenizerPtr = tokenizerPtr;
    }

    public static JavaTokenizer fromFile(String path) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathSegment = arena.allocateFrom(path);
            MemorySegment ptr = (MemorySegment) tokenizer_from_file.invoke(pathSegment);
            return new JavaTokenizer(ptr);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke 'tokenizer_from_file'", e);
        }
    }

    public Encoding encode(String text) {
        return encode(text, true); // 默认添加特殊标记
    }

    public Encoding encode(String text, boolean addSpecialTokens) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment textSegment = arena.allocateFrom(text);
            MemorySegment encodingPtr = (MemorySegment) tokenizer_encode.invoke(this.tokenizerPtr, textSegment, addSpecialTokens);
            return new Encoding(encodingPtr);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke 'tokenizer_encode'", e);
        }
    }

    @Override
    public void close() {
        try {
            tokenizer_free.invoke(this.tokenizerPtr);
        } catch (Throwable e) {
            System.err.println("Error while freeing tokenizer: " + e.getMessage());
        }
    }

    // --- Inner class to represent the result of encoding ---
    public static class Encoding implements AutoCloseable {
        private final MemorySegment encodingPtr;

        private Encoding(MemorySegment encodingPtr) {
            if (encodingPtr.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("Failed to encode text.");
            }
            this.encodingPtr = encodingPtr;
        }

        public int[] getIds() {
            return getIntArray(encoding_get_ids);
        }

        public int[] getAttentionMask() {
            return getIntArray(encoding_get_attention_mask);
        }

        private int[] getIntArray(MethodHandle handle) {
            try (Arena arena = Arena.ofConfined()) {
                // Allocate memory for the length pointer
                MemorySegment lenPtr = arena.allocate(ValueLayout.JAVA_LONG);

                // Call the rust function
                MemorySegment dataPtr = (MemorySegment) handle.invoke(this.encodingPtr, lenPtr);

                // Read the length back from the pointer
                long len = lenPtr.get(ValueLayout.JAVA_LONG, 0);

                if (dataPtr.equals(MemorySegment.NULL) || len == 0) {
                    return new int[0];
                }

                // Create a properly sized memory segment and copy the data
                MemorySegment properSegment = MemorySegment.ofAddress(dataPtr.address()).reinterpret(len * ValueLayout.JAVA_INT.byteSize());
                int[] result = properSegment.toArray(ValueLayout.JAVA_INT);

                // Free the rust-allocated memory
                try {
                    encoding_free_array.invoke(dataPtr, len);
                } catch (Throwable e) {
                    System.err.println("Warning: Failed to free array memory: " + e.getMessage());
                }

                return result;
            } catch (Throwable e) {
                throw new RuntimeException("Failed to get data from encoding", e);
            }
        }

        @Override
        public void close() {
            try {
                encoding_free.invoke(this.encodingPtr);
            } catch (Throwable e) {
                System.err.println("Error while freeing encoding: " + e.getMessage());
            }
        }
    }
}
