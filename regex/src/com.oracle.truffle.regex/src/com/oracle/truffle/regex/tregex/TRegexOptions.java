/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex;

import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.ASTStep;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.NFATraceFinderGenerator;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupPartialTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.TraceFinderDFAStateNode;
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplit;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;

public class TRegexOptions {

    /**
     * Try to pre-calculate results of tree-like expressions (see {@link NFATraceFinderGenerator}).
     * A regular expression is considered tree-like if it does not contain infinite loops (+ or *).
     * This option will increase performance at the cost of startup time and memory usage.
     */
    public static final boolean TRegexEnableTraceFinder = true;

    /**
     * Maximum number of pre-calculated results per TraceFinder DFA. This number must not be higher
     * than 254, because we compress the result indices to {@code byte} in
     * {@link TraceFinderDFAStateNode}, with 255 being reserved for "no result"!
     */
    public static final int TRegexTraceFinderMaxNumberOfResults = 254;

    /**
     * Try to make control flow through DFAs reducible by node splitting (see {@link DFANodeSplit}).
     * This option will increase performance at the cost of startup time and memory usage.
     */
    public static final boolean TRegexEnableNodeSplitter = false;

    /**
     * Maximum size of a DFA after being altered by {@link DFANodeSplit}.
     */
    public static final int TRegexMaxDFASizeAfterNodeSplitting = 4_000;

    /**
     * Minimum number of ranges that have the same high byte to convert into a bit set in a
     * {@link com.oracle.truffle.regex.tregex.matchers.RangeListMatcher} or
     * {@link com.oracle.truffle.regex.tregex.matchers.RangeTreeMatcher}. The threshold value must
     * be greater than 1. Example:
     * 
     * <pre>
     *     [\u1000-\u1020], [\u1030-\u1040], [\u1050-\u1060]
     *     are three ranges that have the same high byte (0x10).
     *     if TRegexRangeToBitSetConversionThreshold is <= 3, they will be converted to a
     *     bit set if they appear in a RangeList or RangeTree matcher.
     * </pre>
     */
    public static final int TRegexRangeToBitSetConversionThreshold = 3;

    /**
     * Bailout threshold for number of nodes in the parser tree ({@link RegexAST} generated by
     * {@link RegexParser}). This number must not be higher than {@link Short#MAX_VALUE}, because we
     * use {@code short} values for indexing AST nodes. The current setting is based on run times of
     * {@code graal/com.oracle.truffle.js.test/js/trufflejs/regexp/npm_extracted/hungry-regexp*.js}
     */
    public static final int TRegexMaxParseTreeSize = 4_000;

    /**
     * Bailout threshold for number of nodes in the NFA ({@link NFA} generated by
     * {@link NFAGenerator}). This number must not be higher than {@link Short#MAX_VALUE}, because
     * we use {@code short} values for indexing NFA nodes. The current setting is based on run times
     * of
     * {@code graal/com.oracle.truffle.js.test/js/trufflejs/regexp/npm_extracted/hungry-regexp*.js}
     */
    public static final int TRegexMaxNFASize = 3_500;

    /**
     * Bailout threshold for number of ASTSuccessor instances allowed in a single {@link ASTStep}.
     * It is possible to construct patterns where the number of NFA transitions grows exponentially.
     * {@link ASTStep} is an intermediate data structure between the AST and the NFA, which is
     * filled eagerly and can cause an {@link OutOfMemoryError} if not capped. Since ASTSuccessors
     * roughly correspond to NFA transitions, the cap has been set to the maximum number of NFA
     * transitions we allow in a single NFA.
     */
    public static final int TRegexMaxNumberOfASTSuccessorsInOneASTStep = Short.MAX_VALUE;

    /**
     * Bailout threshold for number of nodes in the DFA ({@link TRegexDFAExecutorNode} generated by
     * {@link DFAGenerator}). This number must not be higher than {@link Short#MAX_VALUE}, because
     * we use {@code short} values for indexing DFA nodes. The current setting is based on run times
     * of
     * {@code graal/com.oracle.truffle.js.test/js/trufflejs/regexp/npm_extracted/hungry-regexp*.js}
     */
    public static final int TRegexMaxDFASize = 2_400;

    /**
     * Maximum number of entries in the global compilation cache in
     * {@link com.oracle.truffle.regex.RegexLanguage}.
     */
    public static final int RegexMaxCacheSize = 1_000;

    /**
     * Bailout threshold for counted repetitions.
     */
    public static final int TRegexMaxCountedRepetition = 40;

    /**
     * Bailout threshold for number of capture groups. This number must not be higher than 127,
     * because we compress capture group boundary indices to {@code byte} in
     * {@link DFACaptureGroupPartialTransitionNode}!
     */
    public static final int TRegexMaxNumberOfCaptureGroups = 127;

    /**
     * Maximum number of NFA states involved in one DFA transition. This number must not be higher
     * that 255, because the maximum number of NFA states in one DFA transition determines the
     * number of simultaneously tracked result sets (arrays) in capture group tracking mode, which
     * are accessed over byte indices in {@link DFACaptureGroupPartialTransitionNode}.
     */
    public static final int TRegexMaxNumberOfNFAStatesInOneDFATransition = 255;

    static {
        assert TRegexTraceFinderMaxNumberOfResults <= 254;
        assert TRegexMaxParseTreeSize <= Short.MAX_VALUE;
        assert TRegexMaxNFASize <= Short.MAX_VALUE;
        assert TRegexMaxDFASize <= Short.MAX_VALUE;
        assert TRegexMaxDFASizeAfterNodeSplitting <= Short.MAX_VALUE;
        assert TRegexMaxNumberOfCaptureGroups <= 127;
        assert TRegexMaxNumberOfNFAStatesInOneDFATransition <= 255;
        assert TRegexRangeToBitSetConversionThreshold > 1;
    }
}
