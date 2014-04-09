/**
 *  HyperlinkGraph
 *  Copyright 2014 by Michael Peter Christen
 *  First released 08.04.2014 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.search.schema;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.FailType;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.data.meta.URIMetadataNode;

import org.apache.solr.common.SolrDocument;


public class HyperlinkGraph implements Iterable<HyperlinkEdge> {
    
    public final static Set<String> ROOTFNS = new HashSet<String>();
    static {
        for (String s: new String[]{"/", "/index.htm", "/index.html", "/index.php", "/home.htm", "/home.html", "/home.php", "/default.htm", "/default.html", "/default.php"}) {
            ROOTFNS.add(s);
        }
    }
    
    Map<String, HyperlinkEdge> edges;
    Map<DigestURL, Integer> depths;
    String hostname;
    
    public HyperlinkGraph() {
        this.edges = new LinkedHashMap<String, HyperlinkEdge>();
        this.depths = new HashMap<DigestURL, Integer>();
        this.hostname = null;
    }
    
    public void fill(final SolrConnector solrConnector, String hostname, final int maxtime, final int maxnodes) {
        this.hostname = hostname;
        if (hostname.startsWith("www.")) hostname = hostname.substring(4);
        StringBuilder q = new StringBuilder();
        q.append(CollectionSchema.host_s.getSolrFieldName()).append(':').append(hostname).append(" OR ").append(CollectionSchema.host_s.getSolrFieldName()).append(':').append("www.").append(hostname);
        BlockingQueue<SolrDocument> docs = solrConnector.concurrentDocumentsByQuery(q.toString(), CollectionSchema.url_chars_i.getSolrFieldName() + " asc", 0, maxnodes, maxtime, 100, 1,
                CollectionSchema.id.getSolrFieldName(),
                CollectionSchema.sku.getSolrFieldName(),
                CollectionSchema.failreason_s.getSolrFieldName(),
                CollectionSchema.failtype_s.getSolrFieldName(),
                CollectionSchema.inboundlinks_protocol_sxt.getSolrFieldName(),
                CollectionSchema.inboundlinks_urlstub_sxt.getSolrFieldName(),
                CollectionSchema.outboundlinks_protocol_sxt.getSolrFieldName(),
                CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName()
                );
        SolrDocument doc;
        Map<String, FailType> errorDocs = new HashMap<String, FailType>();
        Map<String, HyperlinkEdge> inboundEdges = new HashMap<String, HyperlinkEdge>();
        Map<String, HyperlinkEdge> outboundEdges = new HashMap<String, HyperlinkEdge>();
        Map<String, HyperlinkEdge> errorEdges = new HashMap<String, HyperlinkEdge>();
        try {
            while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                String u = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                String ids = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
                DigestURL from = new DigestURL(u, ASCII.getBytes(ids));
                String errortype = (String) doc.getFieldValue(CollectionSchema.failtype_s.getSolrFieldName());
                FailType error = errortype == null ? null : FailType.valueOf(errortype);
                if (error != null) {
                    errorDocs.put(u, error);
                } else {
                    Iterator<String> links = URIMetadataNode.getLinks(doc, true); // inbound
                    String link;
                    while (links.hasNext()) {
                        link = links.next();
                        try {
                            DigestURL linkurl = new DigestURL(link, null);
                            String edgehash = ids + ASCII.String(linkurl.hash());
                            inboundEdges.put(edgehash, new HyperlinkEdge(from, linkurl, HyperlinkEdge.Type.Inbound));
                        } catch (MalformedURLException e) {}
                    }
                    links = URIMetadataNode.getLinks(doc, false); // outbound
                    while (links.hasNext()) {
                        link = links.next();
                        try {
                            DigestURL linkurl = new DigestURL(link, null);
                            String edgehash = ids + ASCII.String(linkurl.hash());
                            outboundEdges.put(edgehash, new HyperlinkEdge(from, linkurl, HyperlinkEdge.Type.Outbound));
                        } catch (MalformedURLException e) {}
                    }
                }
                if (inboundEdges.size() + outboundEdges.size() > maxnodes) {
                    break;
                }
            }
        } catch (InterruptedException e) {
        } catch (MalformedURLException e) {
        }
        // we use the errorDocs to mark all edges with endpoint to error documents
        Iterator<Map.Entry<String, HyperlinkEdge>> i = inboundEdges.entrySet().iterator();
        Map.Entry<String, HyperlinkEdge> edge;
        while (i.hasNext()) {
            edge = i.next();
            if (errorDocs.containsKey(edge.getValue().target.toNormalform(true))) {
                i.remove();
                edge.getValue().type = HyperlinkEdge.Type.Dead;
                errorEdges.put(edge.getKey(), edge.getValue());
            }
        }
        i = outboundEdges.entrySet().iterator();
        while (i.hasNext()) {
            edge = i.next();
            if (errorDocs.containsKey(edge.getValue().target.toNormalform(true))) {
                i.remove();
                edge.getValue().type = HyperlinkEdge.Type.Dead;
                errorEdges.put(edge.getKey(), edge.getValue());
            }
        }
        // we put all edges together in a specific order which is used to create nodes in a svg display:
        // notes that appear first are possible painted over by nodes coming later.
        // less important nodes shall appear therefore first
        this.edges.putAll(outboundEdges);
        this.edges.putAll(inboundEdges);
        this.edges.putAll(errorEdges);
    }
    
    public int findLinkDepth() {

        int remaining = this.edges.size();
        
        // first find root nodes
        Set<DigestURL> nodes = new HashSet<DigestURL>();
        Set<DigestURL> nextnodes = new HashSet<DigestURL>();
        for (HyperlinkEdge edge: this.edges.values()) {
            String path = edge.source.getPath();
            if (ROOTFNS.contains(path)) {
                if (!this.depths.containsKey(edge.source)) this.depths.put(edge.source, 0);
                if (edge.type == HyperlinkEdge.Type.Inbound && !this.depths.containsKey(edge.target)) this.depths.put(edge.target, 1);
                nodes.add(edge.source);
                nextnodes.add(edge.target);
                remaining--;
            }
        }
        if (nodes.size() == 0) ConcurrentLog.warn("HyperlinkGraph", "could not find a root node for " + hostname + " in " + this.edges.size() + " edges");

        // recusively step into depth and find next level
        int depth = 1;
        while (remaining > 0) {
            boolean found = false;
            nodes = nextnodes;
            nextnodes = new HashSet<DigestURL>();
            for (HyperlinkEdge edge: this.edges.values()) {
                if (nodes.contains(edge.source)) {
                    if (!this.depths.containsKey(edge.source)) this.depths.put(edge.source, depth);
                    if (edge.type == HyperlinkEdge.Type.Inbound && !this.depths.containsKey(edge.target)) this.depths.put(edge.target, depth + 1);
                    nextnodes.add(edge.target);
                    remaining--;
                    found = true;
                }
            }
            depth++;
            if (!found) break; // terminating in case that not all edges are linked together
        }
        if (remaining > 0) ConcurrentLog.warn("HyperlinkGraph", "could not find all edges for " + hostname + ", " + remaining + " remaining.");
        return depth - 1;
    }
    
    public Integer getDepth(DigestURL url) {
        return this.depths.get(url);
    }

    @Override
    public Iterator<HyperlinkEdge> iterator() {
        return this.edges.values().iterator();
    }
    
}