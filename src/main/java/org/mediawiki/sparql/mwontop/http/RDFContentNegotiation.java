/*
 * Copyright (c) 2017 MW2SPARQL developers.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mediawiki.sparql.mwontop.http;

import org.eclipse.rdf4j.common.lang.FileFormat;
import org.eclipse.rdf4j.common.lang.service.FileFormatServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Variant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Thomas Pellissier Tanon
 */
class RDFContentNegotiation {

    private static final Logger LOGGER = LoggerFactory.getLogger(RDFContentNegotiation.class);

    static  <FF extends FileFormat, S> FormatService<S> getServiceForFormat(FileFormatServiceRegistry<FF, S> writerRegistry, Request request) {
        List<Variant> aceptedVariants = buildVariants(writerRegistry.getKeys());
        Variant bestResponseVariant = request.selectVariant(aceptedVariants);
        if (bestResponseVariant == null) {
            throw new NotAcceptableException("No acceptable result format found. Accepted format are: " +
                    aceptedVariants.stream().map(variant -> variant.getMediaType().toString()).collect(Collectors.joining(", ")));
        }
        FF fileFormat = writerRegistry.getFileFormatForMIMEType(bestResponseVariant.getMediaType().toString()).orElseThrow(() -> {
            LOGGER.error("Not able to retrieve writer for " + bestResponseVariant.getMediaType());
            return new InternalServerErrorException("Not able to retrieve writer for " + bestResponseVariant.getMediaType());
        });
        return new FormatService<>(fileFormat, writerRegistry.get(fileFormat).orElseThrow(() -> {
            LOGGER.error("Unable to write " + fileFormat);
            return new InternalServerErrorException("Unable to write " + fileFormat);
        }));
    }

    private static <FF extends FileFormat> List<Variant> buildVariants(Set<FF> acceptedFormats) {
        return Variant.mediaTypes(
                acceptedFormats.stream()
                        .flatMap(fileFormat -> fileFormat.getMIMETypes().stream())
                        .map(MediaType::valueOf)
                        .toArray(MediaType[]::new)
        ).add().build();
    }

    static <FF extends FileFormat> Variant variantForFormat(FF format) {
        return new Variant(MediaType.valueOf(format.getDefaultMIMEType()), (Locale) null, null);
    }

    static class FormatService<S> {

        private FileFormat format;
        private S service;

        FormatService(FileFormat format, S service) {
            this.format = format;
            this.service = service;
        }

        FileFormat getFormat() {
            return format;
        }

        S getService() {
            return service;
        }
    }
}
