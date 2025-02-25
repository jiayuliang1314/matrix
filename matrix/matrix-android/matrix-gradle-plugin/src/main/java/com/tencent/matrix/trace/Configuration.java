package com.tencent.matrix.trace;

import com.tencent.matrix.javalib.util.FileUtil;
import com.tencent.matrix.javalib.util.Util;
import com.tencent.matrix.trace.retrace.MappingCollector;

import java.util.HashSet;

public class Configuration {

    public String packageName;//没有，老的有
    public String mappingDir;
    public String baseMethodMapPath;
    public String methodMapFilePath;
    public String methodNewMapFilePath;
    public String methodNewMapMergeAssetsFilePath;
    public String ignoreMethodMapFilePath;
    public String blockListFilePath;
    public String whiteListFilePath;
    public String traceClassOut;//没有，老的有
    public boolean skipCheckClass;
    public HashSet<String> blockSet = new HashSet<>();
    public HashSet<String> whiteSet = new HashSet<>();

    public Configuration() {
    }

    Configuration(String packageName, String mappingDir, String baseMethodMapPath,
                  String methodMapFilePath, String methodNewMapFilePath,
                  String ignoreMethodMapFilePath, String blockListFilePath, String whiteListFilePath, String traceClassOut, boolean skipCheckClass) {
        this.packageName = packageName;
        this.mappingDir = Util.nullAsNil(mappingDir);
        this.baseMethodMapPath = Util.nullAsNil(baseMethodMapPath);
        this.methodMapFilePath = Util.nullAsNil(methodMapFilePath);
        this.methodNewMapFilePath = Util.nullAsNil(methodNewMapFilePath);
        this.ignoreMethodMapFilePath = Util.nullAsNil(ignoreMethodMapFilePath);
        this.blockListFilePath = Util.nullAsNil(blockListFilePath);
        this.whiteListFilePath = Util.nullAsNil(whiteListFilePath);
        this.traceClassOut = Util.nullAsNil(traceClassOut);
        this.skipCheckClass = skipCheckClass;
    }

    public int parseBlockFile(MappingCollector processor) {//todo
//[package]
//-keeppackage facebook/
//-keeppackage com/squareup/
        String blockStr = TraceBuildConstants.DEFAULT_BLOCK_TRACE
                + FileUtil.readFileAsString(blockListFilePath);

        String[] blockArray = blockStr.trim().replace("/", ".").replace("\r", "").split("\n");

        if (blockArray != null) {
            for (String block : blockArray) {
                if (block.length() == 0) {
                    continue;
                }
                if (block.startsWith("#")) {
                    continue;
                }
                if (block.startsWith("[")) {
                    continue;
                }

                if (block.startsWith("-keepclass ")) {
                    block = block.replace("-keepclass ", "");
                    blockSet.add(processor.proguardClassName(block, block));
                } else if (block.startsWith("-keeppackage ")) {
                    block = block.replace("-keeppackage ", "");
                    blockSet.add(processor.proguardPackageName(block, block));
                }
            }
        }
        return blockSet.size();
    }

    public int parseWhiteFile(MappingCollector processor) {//todo
        String blockStr = FileUtil.readFileAsString(whiteListFilePath);

        String[] blockArray = blockStr.trim().replace("/", ".").replace("\r", "").split("\n");

        if (blockArray != null) {
            for (String block : blockArray) {
                if (block.length() == 0) {
                    continue;
                }
                if (block.startsWith("#")) {
                    continue;
                }
                if (block.startsWith("[")) {
                    continue;
                }

                if (block.startsWith("-keepclass ")) {
                    block = block.replace("-keepclass ", "");
                    whiteSet.add(processor.proguardClassName(block, block));
                } else if (block.startsWith("-keeppackage ")) {
                    block = block.replace("-keeppackage ", "");
                    whiteSet.add(processor.proguardPackageName(block, block));
                }
            }
        }
        return whiteSet.size();
    }


    @Override
    public String toString() {
        return "\n# Configuration" + "\n"
                + "|* packageName:\t" + packageName + "\n"
                + "|* mappingDir:\t" + mappingDir + "\n"
                + "|* baseMethodMapPath:\t" + baseMethodMapPath + "\n"
                + "|* methodMapFilePath:\t" + methodMapFilePath + "\n"
                + "|* ignoreMethodMapFilePath:\t" + ignoreMethodMapFilePath + "\n"
                + "|* blockListFilePath:\t" + blockListFilePath + "\n"
                + "|* whiteListFilePath:\t" + whiteListFilePath + "\n"
                + "|* traceClassOut:\t" + traceClassOut + "\n";
    }

    public static class Builder {

        public String packageName;//没设置呢？ todo 老的有
        public String mappingPath;
        public String baseMethodMap;
        public String methodMapFile;
        public String newMethodMapFile;
        public String ignoreMethodMapFile;
        public String blockListFile;
        public String whiteListFile;
        public String traceClassOut;//老的有
        public boolean skipCheckClass = false;

        public Builder setPackageName(String packageName) {//老的有
            this.packageName = packageName;
            return this;
        }

        public Builder setMappingPath(String mappingPath) {//ok
            this.mappingPath = mappingPath;
            return this;
        }

        public Builder setBaseMethodMap(String baseMethodMap) {//ok
            this.baseMethodMap = baseMethodMap;
            return this;
        }

        public Builder setTraceClassOut(String traceClassOut) {//老的有
            this.traceClassOut = traceClassOut;
            return this;
        }

        public Builder setMethodMapFilePath(String methodMapDir) {//ok
            methodMapFile = methodMapDir;
            return this;
        }

        public Builder setNewMethodMapFilePath(String methodMapDir) {//ok
            newMethodMapFile = methodMapDir;
            return this;
        }

        public Builder setIgnoreMethodMapFilePath(String methodMapDir) {//ok
            ignoreMethodMapFile = methodMapDir;
            return this;
        }

        public Builder setBlockListFile(String blockListFile) {//ok
            this.blockListFile = blockListFile;
            return this;
        }

        public Builder setWhiteListFile(String whiteListFile) {//ok
            this.whiteListFile = whiteListFile;
            return this;
        }

        public Builder setSkipCheckClass(boolean skipCheckClass) {
            this.skipCheckClass = skipCheckClass;
            return this;
        }

        public Configuration build() {
            return new Configuration(packageName, mappingPath, baseMethodMap, methodMapFile, newMethodMapFile, ignoreMethodMapFile, blockListFile, whiteListFile, traceClassOut, skipCheckClass);
        }

    }
}
