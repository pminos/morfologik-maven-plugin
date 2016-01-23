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

import com.carrotsearch.hppc.ByteArrayList;
import morfologik.fsa.FSA5;
import morfologik.tools.SequenceEncoders.IEncoder;

class SequenceAssembler {

    private final byte annotationSeparator;

    private final ByteArrayList src = new ByteArrayList();
    private final ByteArrayList dst = new ByteArrayList();
    private final ByteArrayList tmp = new ByteArrayList();

    private final IEncoder encoder;

    SequenceAssembler(IEncoder encoder) {
        this(encoder, FSA5.DEFAULT_ANNOTATION);
    }

    SequenceAssembler(IEncoder encoder, byte annotationSeparator) {
        this.annotationSeparator = annotationSeparator;
        this.encoder = encoder;
    }

    byte[] encode(byte[] wordForm, byte[] wordLemma, byte[] wordTag) {
        src.clear();
        dst.clear();
        tmp.clear();

        tmp.add(wordForm);
        tmp.add(annotationSeparator);

        src.add(wordForm);
        dst.add(wordLemma);
        encoder.encode(src, dst, tmp);

        tmp.add(annotationSeparator);
        if (wordTag != null) {
            tmp.add(wordTag);
        }

        return tmp.toArray();
    }

}
