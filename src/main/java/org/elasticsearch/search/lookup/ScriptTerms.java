/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.lookup;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.search.CollectionStatistics;
import org.elasticsearch.common.util.MinimalMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Script interface to all information regarding a field.
 * */
public class ScriptTerms extends MinimalMap<String, ScriptTerm> {

    /*
     * TermsInfo Objects that represent the Terms are stored in this map when
     * requested. Information such as frequency, doc frequency and positions
     * information can be retrieved from the TermInfo objects in this map.
     */
    private final Map<String, ScriptTerm> terms = new HashMap<String, ScriptTerm>();

    // the name of this field
    private final String fieldName;

    /*
     * The holds the current reader. We need it to populate the field
     * statistics. We just delegate all requests there
     */
    private ShardTermsLookup shardTermsLookup;

    /*
     * General field statistics such as number of documents containing the
     * field.
     */
    private final CollectionStatistics fieldStats;

    /*
     * Uodate posting lists in all TermInfo objects
     */
    void setReader(AtomicReader reader) {
        for (ScriptTerm ti : terms.values()) {
            ti.setNextReader(reader);
        }
    }

    /*
     * Represents a field in a document. Can be used to return information on
     * statistics of this field. Information on specific terms in this field can
     * be accessed by calling get(String term).
     */
    public ScriptTerms(String fieldName, ShardTermsLookup shardTermsLookup) throws IOException {

        assert fieldName != null;
        this.fieldName = fieldName;

        assert shardTermsLookup != null;
        this.shardTermsLookup = shardTermsLookup;

        fieldStats = shardTermsLookup.getIndexSearcher().collectionStatistics(fieldName);
    }

    /* get number of documents containing the field */
    public long docCount() throws IOException {
        return fieldStats.docCount();
    }

    /* get sum of the number of words over all documents that were indexed */
    public long sumttf() throws IOException {
        return fieldStats.sumTotalTermFreq();
    }

    /*
     * get the sum of doc frequencies over all words that appear in any document
     * that has the field.
     */
    public long sumdf() throws IOException {
        return fieldStats.sumDocFreq();
    }

    // TODO: might be good to get the field lengths here somewhere?

    /*
     * Returns a TermInfo object that can be used to access information on
     * specific terms. flags can be set as described in TermInfo.
     * 
     * TODO: here might be potential for running time improvement? If we knew in
     * advance which terms are requested, we could provide an array which the
     * user could then iterate over.
     */
    public ScriptTerm get(Object key, int flags) {
        String termString = (String) key;
        ScriptTerm termInfo = terms.get(termString);
        // see if we initialized already...
        if (termInfo == null) {
            termInfo = new ScriptTerm(termString, fieldName, shardTermsLookup, flags);
            terms.put(termString, termInfo);
        }
        termInfo.validateFlags(flags);
        return termInfo;
    }

    /*
     * Returns a TermInfo object that can be used to access information on
     * specific terms. flags can be set as described in TermInfo.
     */
    public ScriptTerm get(Object key) {
        // per default, do not initialize any positions info
        return get(key, ShardTermsLookup.FLAG_FREQUENCIES);
    }

    public void setDocIdInTerms(int docId) {
        for (ScriptTerm ti : terms.values()) {
            ti.setNextDoc(docId);
        }
    }

}
