/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.rpc.cors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class OriginValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger("jsonrpc");

    private URI[] origins;
    private boolean allowAllOrigins;

    public OriginValidator() {
        this.origins = new URI[0];
    }

    public OriginValidator(String uriList) {
        if (uriList == null) {
            this.origins = new URI[0];
        } else if ("*".equals(uriList.trim())) {
            this.allowAllOrigins = true;
        } else {
            try {
                this.origins = toUris(uriList);
            } catch (URISyntaxException e) {
                LOGGER.error("Error creating OriginValidator, origins {}, {}", uriList, e);

                // no origin
                this.origins = new URI[0];
            }
        }
    }

    public boolean isValidOrigin(String origin) {
        if (this.allowAllOrigins) {
            return true;
        }

        URI originUri = null;

        try {
            originUri = new URI(origin);
        } catch (URISyntaxException e) {
            return false;
        }

        for (URI uri : origins) {
            if (originUri.equals(uri)) {
                return true;
            }
        }

        return false;
    }

    public boolean isValidReferer(String referer) {
        if (this.allowAllOrigins) {
            return true;
        }

        URL refererUrl = null;

        try {
            refererUrl = new URL(referer);
        } catch (MalformedURLException e) {
            return false;
        }

        String refererProtocol = refererUrl.getProtocol();

        if (refererProtocol == null) {
            return false;
        }

        String refererHost = refererUrl.getHost();

        if (refererHost == null) {
            return false;
        }

        int refererPort = refererUrl.getPort();

        for (int k = 0; k < origins.length; k++) {
            if (refererProtocol.equals(origins[k].getScheme()) &&
                    refererHost.equals(origins[k].getHost()) &&
                    refererPort == origins[k].getPort()) {
                return true;
            }
        }

        return false;
    }

    private static URI[] toUris(@Nonnull String list) throws URISyntaxException {
        String[] elements = list.split(" ");
        URI[] uris = new URI[elements.length];

        for (int k = 0; k < elements.length; k++) {
            uris[k] = new URI(elements[k].trim());
        }

        return uris;
    }
}
