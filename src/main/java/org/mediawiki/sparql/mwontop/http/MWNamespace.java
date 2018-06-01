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

import org.mediawiki.sparql.mwontop.api.SiteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MWNamespace {
    private static final Logger LOGGER = LoggerFactory.getLogger(MWNamespace.class);
    private static Map<String, Map<String, String>> NAMESPACES = new HashMap<>();
    private static Pattern NAMESPACE_URI_REGEX = Pattern.compile("//([^/]*)/wiki/([^:^>]*:)?([^>]+)");

    private static Map<String, String> getNamespaces(String projectHost) {
        if (!NAMESPACES.containsKey(projectHost)) {
            Map<String, String> ns = new HashMap<>();
            try {
                LOGGER.info("Loading namespaces for "+projectHost);
                SiteInfo siteInfo = SiteInfo.loadSiteInfo(projectHost);
                siteInfo.getNamespaceNames().forEach((nsId, nsName) -> ns.put("ns" + nsId, nsName));
                siteInfo.getAllNamespaceNames().forEach((nsName, nsId) -> ns.put(nsName, "ns" + nsId));
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
            NAMESPACES.put(projectHost, ns);
        }
        return NAMESPACES.get(projectHost);
    }

    /**
     * Various WikiMedia projects utilize different prefixes for the same namespaces.
     * For instance, "Category" in en.wikipedia is the same as "Категория" in ru.wikiquote
     * This method scans input string for urls and used for both:
     * <ul>
     * <li>Encoding normal prefix to nsdd where dd is namespace-id (e.g. /Template: to /ns10:)
     * <li>Decoding nsdd to project-specific namespace prefixes (e.g. /ns6: to /File:)
     * </ul>
     * nsdd-notation is supported by /resources/mapping.ttl
     * and invented in order to integrate ontop with labs replica databases.
     * In addition to that, blazegraph, utilized as an official Wikidata query engine
     * is very sensitive regarding encoding of umlauts in namespaces and page titles
     *
     * @param text         to be processed with urls to WikiMedia projects
     * @param decodeTitles defines whenever namespace and page titles should be decoded or encoded
     * @return text with mutated namespaces in WikiMedia urls
     */
    static String mutateNamespace(String text, boolean decodeTitles) {
        StringBuffer buf = new StringBuffer();
        Matcher m = NAMESPACE_URI_REGEX.matcher(text);
        while (m.find()) {
            String namespace = m.group(2) != null ? m.group(2).substring(0, m.group(2).length() - 1) : "";
            String pageTitle = m.group(3);
            try {
                if (decodeTitles) {
                    namespace = URLDecoder.decode(namespace, "UTF-8");
                    pageTitle = URLDecoder.decode(pageTitle, "UTF-8").
                            replace("`", "%60").replace("\"", "%22");
                }
                if (getNamespaces(m.group(1)).containsKey(namespace)) {
                    namespace = getNamespaces(m.group(1)).get(namespace);
                }
                if (!decodeTitles) {
                    namespace = URLEncoder.encode(namespace, "UTF-8");
                    pageTitle = URLEncoder.encode(pageTitle, "UTF-8");
                }
            } catch (UnsupportedEncodingException ignored) {
            }
            if (!namespace.isEmpty())
                pageTitle = namespace + ':' + pageTitle;
            m.appendReplacement(buf, text.substring(m.start(), m.group(2) != null ? m.start(2) : m.start(3)) +
                    pageTitle + text.substring(m.end(3), m.end()));
        }
        m.appendTail(buf);
        return buf.toString();
    }
}
