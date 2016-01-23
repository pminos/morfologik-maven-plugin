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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import morfologik.fsa.FSA;
import morfologik.fsa.FSABuilder;
import morfologik.fsa.FSASerializer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

class MorfologikHelper {

    private final Log log;

    MorfologikHelper(Log log) throws MojoExecutionException {
        this.log = log;
    }

    void process(File inputFile, File outputFile, SequenceAssembler encoder,
            byte separatorByte, boolean createSynth, boolean inputSorted,
            final FSASerializer serializer) throws MojoExecutionException {

        long lnum = 0;
        ArrayList<byte[]> lines = new ArrayList<byte[]>();
        ArrayList<byte[]> columns = new ArrayList<byte[]>();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        BufferedInputStream input;
        try {
            input = new BufferedInputStream(new FileInputStream(inputFile));
        } catch (FileNotFoundException ex) {
            throw new MojoExecutionException("Input file does not exist: "
                    + inputFile.getAbsolutePath());
        }
        try {

            int b;
            do {
                b = input.read();
                switch (b) {
                    case '\r':
                        // Ignore CR
                        continue;

                    case '\t':
                        columns.add(bos.toByteArray());
                        bos.reset();
                        break;

                    case -1:
                    // Process EOF as if we encountered \n. fall-through.

                    case '\n':
                        lnum++;
                        if (bos.size() == 0 && columns.isEmpty()) {
                            if (b != -1) {
                                log.warn(String.format(Locale.ROOT,
                                        "Ignoring empty line %d.", lnum));
                            }
                            break;
                        }

                        columns.add(bos.toByteArray());
                        bos.reset();

                        if (columns.size() < 2 || columns.size() > 3) {
                            throw new MojoExecutionException(
                                    String.format(Locale.ROOT,
                                            "Line %d must contain 2 or 3"
                                            + " columns, has %d",
                                            lnum, columns.size()));
                        }

                        if (columns.size() == 2 && !createSynth) {
                            log.warn(String.format(Locale.ROOT,
                                    "Line %d has %d columns",
                                    lnum, columns.size()));
                        }

                        byte[] wf = columns.get(0);
                        byte[] lm = columns.get(1);
                        boolean wordHasSeparator = contains(wf, separatorByte);
                        boolean lemmaHasSeparator = contains(lm, separatorByte);
                        if (wordHasSeparator || lemmaHasSeparator) {
                            throw new MojoExecutionException(
                                    String.format(Locale.ROOT,
                                            "Word or lemma in line %d contains"
                                            + " the annotation byte '%s'",
                                            lnum, (char) separatorByte
                                    ));
                        }

                        lines.add(encoder.encode(wf, lm,
                                columns.size() > 2 ? columns.get(2) : null));

                        columns.clear();
                        break;

                    default:
                        bos.write(b);
                }
            } while (b != -1);
        } catch (IOException ex) {
            throw new MojoExecutionException("IO Exception", ex);
        } finally {
            try {
                input.close();
            } catch (IOException ex) {
            }
        }

        process(lines, outputFile, inputSorted, serializer);

    }

    private void process(ArrayList<byte[]> input, File outputFile,
            boolean inputSorted, FSASerializer serializer)
            throws MojoExecutionException {
        final FSABuilder builder = new FSABuilder();
        final OutputStream output;
        final FSA fsa;

        if (!input.isEmpty()) {
            boolean hasBOM = checkUtf8Bom(input.get(0));
            if (hasBOM) {
                log.warn("input starts with UTF-8 BOM bytes which is most"
                        + " likely not what you want. Use header-less UTF-8"
                        + " file (unless you are encoding plain bytes in"
                        + " which case this message doesn't apply).");
            }
        }

        if (!inputSorted) {
            Collections.sort(input, FSABuilder.LEXICAL_ORDERING);
        } else {
            for (int i = 1; i < input.size(); i++) {
                if (FSABuilder.compare(input.get(i - 1), 0,
                        input.get(i - 1).length, input.get(i), 0,
                        input.get(i).length) > 0) {
                    throw new MojoExecutionException("Input is not sorted.");
                }
            }
        }
        for (byte[] bb : input) {
            builder.add(bb, 0, bb.length);
        }
        fsa = builder.complete();

        try {
            output = new FileOutputStream(outputFile);
            serializer.serialize(fsa, new BufferedOutputStream(output)).close();
        } catch (OutOfMemoryError ex) {
            throw new MojoExecutionException("Out of memory");
        } catch (IOException ex) {
            throw new MojoExecutionException("IO Exception", ex);
        }
    }

    private boolean contains(byte[] a, byte b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == b) {
                return true;
            }
        }
        return false;
    }

    private boolean checkUtf8Bom(byte[] a) {
        if (a.length > 2) {
            if (a[0] == 0xef && a[1] == 0xbb && a[2] == 0xbf) {
                return true;
            }
        }
        return false;
    }

}
