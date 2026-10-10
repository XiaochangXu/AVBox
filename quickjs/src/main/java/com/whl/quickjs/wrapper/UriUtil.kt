/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.whl.quickjs.wrapper

import android.net.Uri
import android.text.TextUtils

/**
 * Utility methods for manipulating URIs.
 */
object UriUtil {

    private const val INDEX_COUNT = 4
    private const val SCHEME_COLON = 0
    private const val PATH = 1
    private const val QUERY = 2
    private const val FRAGMENT = 3

    @JvmStatic
    fun resolveToUri(baseUri: String?, referenceUri: String?): Uri =
        Uri.parse(resolve(baseUri, referenceUri))

    @JvmStatic
    fun resolve(baseUri: String?, referenceUri: String?): String {
        val uri = StringBuilder()

        var base = baseUri ?: ""
        var reference = referenceUri ?: ""

        val refIndices = getUriIndices(reference)
        if (refIndices[SCHEME_COLON] != -1) {
            uri.append(reference)
            removeDotSegments(uri, refIndices[PATH], refIndices[QUERY])
            return uri.toString()
        }

        val baseIndices = getUriIndices(base)
        if (refIndices[FRAGMENT] == 0) {
            return uri.append(base, 0, baseIndices[FRAGMENT]).append(reference).toString()
        }

        if (refIndices[QUERY] == 0) {
            return uri.append(base, 0, baseIndices[QUERY]).append(reference).toString()
        }

        if (refIndices[PATH] != 0) {
            val baseLimit = baseIndices[SCHEME_COLON] + 1
            uri.append(base, 0, baseLimit).append(reference)
            return removeDotSegments(uri, baseLimit + refIndices[PATH], baseLimit + refIndices[QUERY])
        }

        if (reference[refIndices[PATH]] == '/') {
            uri.append(base, 0, baseIndices[PATH]).append(reference)
            return removeDotSegments(uri, baseIndices[PATH], baseIndices[PATH] + refIndices[QUERY])
        }

        if (baseIndices[SCHEME_COLON] + 2 < baseIndices[PATH] &&
            baseIndices[PATH] == baseIndices[QUERY]
        ) {
            uri.append(base, 0, baseIndices[PATH]).append('/').append(reference)
            return removeDotSegments(
                uri,
                baseIndices[PATH],
                baseIndices[PATH] + refIndices[QUERY] + 1,
            )
        }

        val lastSlashIndex = base.lastIndexOf('/', baseIndices[QUERY] - 1)
        val baseLimit = if (lastSlashIndex == -1) baseIndices[PATH] else lastSlashIndex + 1
        uri.append(base, 0, baseLimit).append(reference)
        return removeDotSegments(uri, baseIndices[PATH], baseLimit + refIndices[QUERY])
    }

    @JvmStatic
    fun isAbsolute(uri: String?): Boolean =
        uri != null && getUriIndices(uri)[SCHEME_COLON] != -1

    @JvmStatic
    fun removeQueryParameter(uri: Uri, queryParameterName: String): Uri {
        val builder = uri.buildUpon()
        builder.clearQuery()
        for (key in uri.queryParameterNames) {
            if (key != queryParameterName) {
                for (value in uri.getQueryParameters(key)) {
                    builder.appendQueryParameter(key, value)
                }
            }
        }
        return builder.build()
    }

    private fun removeDotSegments(uri: StringBuilder, offset: Int, limit: Int): String {
        var currentLimit = limit
        var pathOffset = offset
        if (pathOffset >= currentLimit) {
            return uri.toString()
        }
        if (uri[pathOffset] == '/') {
            pathOffset++
        }
        var segmentStart = pathOffset
        var i = pathOffset
        while (i <= currentLimit) {
            val nextSegmentStart: Int
            if (i == currentLimit) {
                nextSegmentStart = i
            } else if (uri[i] == '/') {
                nextSegmentStart = i + 1
            } else {
                i++
                continue
            }
            if (i == segmentStart + 1 && uri[segmentStart] == '.') {
                uri.delete(segmentStart, nextSegmentStart)
                currentLimit -= nextSegmentStart - segmentStart
                i = segmentStart
            } else if (i == segmentStart + 2 &&
                uri[segmentStart] == '.' &&
                uri[segmentStart + 1] == '.'
            ) {
                val prevSegmentStart = uri.lastIndexOf("/", segmentStart - 2) + 1
                val removeFrom = if (prevSegmentStart > pathOffset) prevSegmentStart else pathOffset
                uri.delete(removeFrom, nextSegmentStart)
                currentLimit -= nextSegmentStart - removeFrom
                segmentStart = prevSegmentStart
                i = prevSegmentStart
            } else {
                i++
                segmentStart = i
            }
        }
        return uri.toString()
    }

    private fun getUriIndices(uriString: String?): IntArray {
        val indices = IntArray(INDEX_COUNT)
        if (TextUtils.isEmpty(uriString)) {
            indices[SCHEME_COLON] = -1
            return indices
        }

        val value = uriString!!

        val length = value.length
        var fragmentIndex = value.indexOf('#')
        if (fragmentIndex == -1) {
            fragmentIndex = length
        }
        var queryIndex = value.indexOf('?')
        if (queryIndex == -1 || queryIndex > fragmentIndex) {
            queryIndex = fragmentIndex
        }
        var schemeIndexLimit = value.indexOf('/')
        if (schemeIndexLimit == -1 || schemeIndexLimit > queryIndex) {
            schemeIndexLimit = queryIndex
        }
        var schemeIndex = value.indexOf(':')
        if (schemeIndex > schemeIndexLimit) {
            schemeIndex = -1
        }

        val hasAuthority = schemeIndex + 2 < queryIndex &&
            value[schemeIndex + 1] == '/' &&
            value[schemeIndex + 2] == '/'
        val pathIndex: Int
        if (hasAuthority) {
            pathIndex = value.indexOf('/', schemeIndex + 3).let {
                if (it == -1 || it > queryIndex) queryIndex else it
            }
        } else {
            pathIndex = schemeIndex + 1
        }

        indices[SCHEME_COLON] = schemeIndex
        indices[PATH] = pathIndex
        indices[QUERY] = queryIndex
        indices[FRAGMENT] = fragmentIndex
        return indices
    }
}
