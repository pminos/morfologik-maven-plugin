/*
 * Copyright 2015 Panagiotis Minos.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ioperm.maven.plugin.morfologik;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import morfologik.fsa.CFSA2Serializer;
import morfologik.fsa.FSA5;
import morfologik.fsa.FSA5Serializer;
import morfologik.fsa.FSASerializer;
import morfologik.stemming.EncoderType;
import morfologik.tools.SequenceEncoders;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Mojo for creating morphological dictionaries for use with <a href="http://morfologik.blogspot.com/">morfologik-stemming</a>.
 *
 * @author Panagiotis Minos
 */
@Mojo(name = "generate-morphologic", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class MorfologikMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * Input Directory.
     */
    @Parameter(defaultValue = "${basedir}/src/main/morfologik", required = true)
    private File inputDirectory;

    /**
     * Output Directory.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-resources/morfologik", required = true)
    private File outputDirectory;

    /**
     * Input file.
     */
    @Parameter(required = false)
    private File inputFile;

    /**
     * Output filename
     */
    @Parameter(required = false)
    private String outputFilename;

    /**
     * Output package name
     */
    @Parameter(defaultValue = "", required = false)
    private String packageName;

    /**
     * Encoder used for compressing inflected forms. Any of: [SUFFIX, PREFIX,
     * INFIX, NONE]
     */
    @Parameter(defaultValue = "suffix")
    private String encoder;

    /**
     * Custom annotation separator character
     */
    @Parameter(defaultValue = "+")
    private String annotation;

    /**
     * FSA format. One of [fsa5, cfsa2]
     */
    @Parameter(defaultValue = "cfsa2")
    private String format;

    /**
     * Defines the encoding of the input file.
     */
    @Parameter(defaultValue = "UTF-8")
    private String encoding;

    /**
     * Should we skip execution?
     */
    @Parameter(defaultValue = "false")
    private boolean skip;

    /**
     * Dictionary type. One of [morphologic, synthesis]
     */
    @Parameter(defaultValue = "morphologic")
    private String type;

    private boolean synth;

    MorfologikMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            return;
        }
        if("morphological".equals(type)) {
            synth=false;
        }
        if("synthesis".equals(type)) {
            synth=true;
        }
        this.inputDirectory = getAbsolutePath(this.inputDirectory);
        this.outputDirectory = getAbsolutePath(this.outputDirectory);
        final Resource toAdd = new Resource();
        toAdd.setDirectory(outputDirectory.getAbsolutePath());
        this.project.getBuild().getResources().add(toAdd);

        List<File> files = new ArrayList<File>();

        if (inputFile != null) {
            files.add(inputFile);
        } else {
            if (inputDirectory.isDirectory()) {
                for (File f : FileUtils.listFiles(inputDirectory, null, true)) {
                    if (f.isFile()) {
                        files.add(f);
                    }
                }
            }
        }

        for (File in : files) {
            getLog().info("processing " + in.getAbsolutePath());
            if (!in.exists()) {
                throw new MojoExecutionException("Input file does not exist: "
                        + in.getAbsolutePath());
            }

            File dict = getFilenameDict(in);
            File info = getFilenameInfo(in);
            getLog().info("dict " + dict.getAbsolutePath());
            getLog().info("info " + info.getAbsolutePath());
            /* Generate only if needs to */
            if (in.lastModified() <= dict.lastModified()
                    && in.lastModified() <= info.lastModified()) {
                getLog().info("  " + dict.getPath() + " is up to date."
                        + in.exists());
            } else {
                compile(in, dict);
                writeInfo(info);
            }
        }
    }

    private void compile(File in, File out) throws MojoExecutionException {

        this.getLog().info(String.format("Converting %s to %s", in.toString(),
                out.toString()));

        EncoderType encType = EncoderType.SUFFIX;
        if (encoder != null) {
            try {
                encType = EncoderType.valueOf(encoder.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new MojoExecutionException("Invalid encoder: "
                        + encoder + ", " + "allowed values: "
                        + Arrays.toString(EncoderType.values()));
            }
        }
        byte separatorByte = FSA5.DEFAULT_ANNOTATION;

        if (annotation != null) {
            String sep = annotation;
            sep = StringEscapeUtils.unescapeJava(sep);
            if (sep.length() != 1) {
                throw new MojoExecutionException(
                        "Field separator must be a single character: " + sep);
            }
            separatorByte = checkSingleByte(sep, Charset.defaultCharset());
        }

        FSASerializer serializer;
        if ("FSA5".equalsIgnoreCase(format)) {
            serializer = new FSA5Serializer();
        } else if ("CFSA2".equalsIgnoreCase(format)) {
            serializer = new CFSA2Serializer();
        } else {
            throw new MojoExecutionException(
                    String.format("%s is not a valid serializer", format));
        }

        SequenceAssembler assembler;
        if (synth) {
            assembler = new SynthSequenceAssembler(
                    SequenceEncoders.forType(encType), separatorByte);
        } else {
            assembler = new SequenceAssembler(
                    SequenceEncoders.forType(encType), separatorByte);
        }

        MorfologikHelper tool = new MorfologikHelper(getLog());
        tool.process(in, out, assembler, separatorByte, synth, false,
                serializer);

        Resource resource = new Resource();
        try {
            resource.setDirectory(outputDirectory.getCanonicalPath());
        } catch (IOException ex) {
            Logger.getLogger(MorfologikMojo.class.getName()).log(Level.SEVERE, null, ex);
        }
        //resource.addInclude("**/*");
        project.addResource(resource);

    }

    private byte checkSingleByte(String chr, Charset charset)
            throws MojoExecutionException {
        byte bytes[] = chr.getBytes(charset);
        if (bytes.length == 1) {
            return bytes[0];
        }

        throw new MojoExecutionException("Annotation character must be single"
                + "-byte values, " + chr + " has " + chr.getBytes().length
                + " bytes.");
    }

    private void writeInfo(File info) throws MojoExecutionException {
        List<String> lines = new ArrayList<String>();
        lines.add(String.format("#Timestamp " + System.currentTimeMillis()));
        lines.add(String.format("#FSA format <%s>", this.format));
        lines.add(String.format("fsa.dict.encoding=%s", this.encoding));
        lines.add(String.format("fsa.dict.encoder=%s", this.encoder));
        lines.add(String.format("fsa.dict.separator=%s", this.annotation));
        try {
            FileUtils.writeLines(info, lines);
        } catch (IOException ex) {
            throw new MojoExecutionException("", ex);
        }
    }

    private File getAbsolutePath(File path) {
        if (path == null || path.isAbsolute()) {
            return path;
        }
        return new File(this.project.getBasedir().getAbsolutePath(),
                path.getPath());
    }

    private File getFilenameDict(File path) {
        return getFilename(path, "dict");
    }

    private File getFilenameInfo(File path) {
        return getFilename(path, "info");
    }

    private File getFilename(File path, String ext) {

        String outputPath = "";
        if (packageName != null && !packageName.isEmpty()) {
            outputPath = packageName.replace('.', '/') + '/';
        }
        if (this.inputFile != null && this.outputFilename != null) {
            outputPath += outputFilename;
        } else {
            outputPath += FilenameUtils.getBaseName(path.getAbsolutePath());
        }
        outputPath += "." + ext;
        File r = new File(this.outputDirectory, outputPath);
        r.getParentFile().mkdirs();
        return r;
    }

}
