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
import java.util.Hashtable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MWNamespace {
    private static final Logger LOGGER = LoggerFactory.getLogger(SPARQLActions.class);
	private static Map<String, Map<String, String>> NAMESPACES = new Hashtable<>();
	private static Pattern NAMESPACE_URI_REGEX = Pattern.compile("//([^/]*)/wiki/([^:]*:)?");

	private static Map<String, String> getNamespaces(String projectHost) {
		if (!NAMESPACES.containsKey(projectHost)) {
            Map<String, String> ns = new Hashtable<>();
            try {
				SiteInfo siteInfo = SiteInfo.loadSiteInfo(projectHost);
				siteInfo.getNamespaceNames().forEach((nsId, nsName) -> ns.put("ns" + nsId + ":", nsName));
				siteInfo.getAllNamespaceNames().forEach((nsName, nsId) -> ns.put("ns" + nsId + ":", nsName));
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
     * and invented in order to integrate ontop with labs replica databases
     *
     * @param input text, that contains urls to WikiMedia projects
     * @return text with mutated namespaces in WikiMedia urls
     */
    static String mutateNamespace(String input) {
		Matcher m = NAMESPACE_URI_REGEX.matcher(input);
        StringBuffer buf = new StringBuffer();
        while (m.find()) {
            if (m.group(2) != null && getNamespaces(m.group(1)).containsKey(m.group(2))) {
                m.appendReplacement(buf, input.substring(m.start(), m.start(2)) +
                        getNamespaces(m.group(1)).get(m.group(2)) + input.substring(m.end(2), m.end()));
                break;
            }
        }
        m.appendTail(buf);
        return buf.toString();
    }
}
