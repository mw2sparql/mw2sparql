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

package org.mediawiki.sparql.mwontop.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Thomas Pellissier Tanon
 */
public class SiteInfo {

    private static final ObjectReader OBJECT_READER = new ObjectMapper().readerFor(JsonNode.class);

    private String wikiId;

    private Map<Integer, String> namespacesNames = new TreeMap<>();
    private Map<String, Integer> allNamespacesNames = new TreeMap<>();

    private SiteInfo(JsonNode apiResult) throws IOException {
        if (!apiResult.has("query")) {
            throw new IOException("Invalid API result");
        }

        JsonNode queryNode = apiResult.get("query");
        if (!queryNode.has("general") || !queryNode.has("namespaces")) {
            throw new IOException("Invalid API result");
        }

        JsonNode generalNode = queryNode.get("general");
        wikiId = readStringProperty(generalNode, "wikiid");

        JsonNode namespacesNode = queryNode.get("namespaces");
        for (JsonNode namespaceNode : namespacesNode) {
            Integer namespaceId = readIntegerProperty(namespaceNode, "id");
            if (namespaceNode.has("*")) {
                String namespaceName = readStringProperty(namespaceNode, "*");
                namespacesNames.put(namespaceId, namespaceName);
                allNamespacesNames.put(namespaceName, namespaceId);
            }
            if (namespaceNode.has("canonical")) {
                allNamespacesNames.put(readStringProperty(namespaceNode, "canonical"), namespaceId);
            }
        }

        JsonNode namespacesAliasesNode = queryNode.get("namespacealiases");
        for (JsonNode namespaceAliasNode : namespacesAliasesNode) {
            allNamespacesNames.put(readStringProperty(namespaceAliasNode, "*"), readIntegerProperty(namespaceAliasNode, "id"));
        }
    }

    public static SiteInfo loadSiteInfo(String projectHost) throws IOException {
        URL url = UriBuilder.fromUri("https://" + projectHost)
                .path("/w/api.php")
                .replaceQuery("action=query&meta=siteinfo&siprop=general%7Cnamespaces%7Cnamespacealiases&format=json").build().toURL();
        return new SiteInfo(OBJECT_READER.readValue(url));
    }

    public String getWikiId() {
        return wikiId;
    }

    public Map<Integer, String> getNamespaceNames() {
        return namespacesNames;
    }

    public Map<String, Integer> getAllNamespaceNames() {
        return allNamespacesNames;
    }

    private String readStringProperty(JsonNode node, String key) throws IOException {
        if (!node.has(key) || !node.get(key).isTextual()) {
            throw new IOException("Invalid API result");
        }
        return node.get(key).asText();
    }

    private int readIntegerProperty(JsonNode node, String key) throws IOException {
        if (!node.has(key) || !node.get(key).isInt()) {
            throw new IOException("Invalid API result");
        }
        return node.get(key).asInt();
    }
}
