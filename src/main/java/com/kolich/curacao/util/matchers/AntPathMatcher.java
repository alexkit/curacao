/*
 * Copyright 2002-2013 the original author or authors.
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

package com.kolich.curacao.util.matchers;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * PathMatcher implementation for Ant-style path patterns. Examples are provided below.
 *
 * <p>Part of this mapping code has been kindly borrowed from <a href="http://ant.apache.org">Apache Ant</a>.
 *
 * <p>The mapping matches URLs using the following rules:<br> <ul> <li>? matches one character</li> <li>* matches zero
 * or more characters</li> <li>** matches zero or more 'directories' in a path</li> </ul>
 *
 * <p>Some examples:<br> <ul> <li>{@code com/t?st.jsp} - matches {@code com/test.jsp} but also
 * {@code com/tast.jsp} or {@code com/txst.jsp}</li> <li>{@code com/*.jsp} - matches all
 * {@code .jsp} files in the {@code com} directory</li> <li>{@code com/&#42;&#42;/test.jsp} - matches all
 * {@code test.jsp} files underneath the {@code com} path</li> <li>{@code org/springframework/&#42;&#42;/*.jsp}
 * - matches all {@code .jsp} files underneath the {@code org/springframework} path</li>
 * <li>{@code org/&#42;&#42;/servlet/bla.jsp} - matches {@code org/springframework/servlet/bla.jsp} but also
 * {@code org/springframework/testing/servlet/bla.jsp} and {@code org/servlet/bla.jsp}</li> </ul>
 *
 * @author Alef Arendsen
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 16.07.2003
 */
public final class AntPathMatcher {

	/** Default path separator: "/" */
	public static final String DEFAULT_PATH_SEPARATOR = "/";

	private String pathSeparator = DEFAULT_PATH_SEPARATOR;

	private final Map<String, AntPathStringMatcher> stringMatcherCache =
			new ConcurrentHashMap<String, AntPathStringMatcher>(256);

	private boolean trimTokens = true;
	
	private AntPathMatcher() {}
	private static class LazyHolder {
		private static final AntPathMatcher instance__ = new AntPathMatcher();
	}
	public static final AntPathMatcher getInstance() {
		return LazyHolder.instance__;
	}
	
	/*
	public boolean matches(String pattern, String path) {
		return doMatch(pattern, path, true, null);
	}
	*/
	
	public Map<String, String> extractUriTemplateVariables(String pattern, String path) {
		final Map<String, String> variables = Maps.newLinkedHashMap();
		return doMatch(pattern, path, true, variables) ?
			// Extracted path variables are returned to the caller bound
			// within an immutable (unmodifiable) map instance.
			Collections.unmodifiableMap(variables) :
			null;
	}

	/**
	 * Actually match the given {@code path} against the given {@code pattern}.
	 * @param pattern the pattern to match against
	 * @param path the path String to test
	 * @param fullMatch whether a full pattern match is required (else a pattern match
	 * as far as the given base path goes is sufficient)
	 * @return {@code true} if the supplied {@code path} matched, {@code false} if it didn't
	 */
	private boolean doMatch(String pattern, String path, boolean fullMatch,
			Map<String, String> uriTemplateVariables) {

		if (path.startsWith(this.pathSeparator) != pattern.startsWith(this.pathSeparator)) {
			return false;
		}

		String[] pattDirs = tokenizeToStringArray(pattern, this.pathSeparator, this.trimTokens, true);
		String[] pathDirs = tokenizeToStringArray(path, this.pathSeparator, this.trimTokens, true);

		int pattIdxStart = 0;
		int pattIdxEnd = pattDirs.length - 1;
		int pathIdxStart = 0;
		int pathIdxEnd = pathDirs.length - 1;

		// Match all elements up to the first **
		while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
			String patDir = pattDirs[pattIdxStart];
			if ("**".equals(patDir)) {
				break;
			}
			if (!matchStrings(patDir, pathDirs[pathIdxStart], uriTemplateVariables)) {
				return false;
			}
			pattIdxStart++;
			pathIdxStart++;
		}

		if (pathIdxStart > pathIdxEnd) {
			// Path is exhausted, only match if rest of pattern is * or **'s
			if (pattIdxStart > pattIdxEnd) {
				return (pattern.endsWith(this.pathSeparator) ? path.endsWith(this.pathSeparator) :
						!path.endsWith(this.pathSeparator));
			}
			if (!fullMatch) {
				return true;
			}
			if (pattIdxStart == pattIdxEnd && pattDirs[pattIdxStart].equals("*") && path.endsWith(this.pathSeparator)) {
				return true;
			}
			for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
				if (!pattDirs[i].equals("**")) {
					return false;
				}
			}
			return true;
		}
		else if (pattIdxStart > pattIdxEnd) {
			// String not exhausted, but pattern is. Failure.
			return false;
		}
		else if (!fullMatch && "**".equals(pattDirs[pattIdxStart])) {
			// Path start definitely matches due to "**" part in pattern.
			return true;
		}

		// up to last '**'
		while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
			String patDir = pattDirs[pattIdxEnd];
			if (patDir.equals("**")) {
				break;
			}
			if (!matchStrings(patDir, pathDirs[pathIdxEnd], uriTemplateVariables)) {
				return false;
			}
			pattIdxEnd--;
			pathIdxEnd--;
		}
		if (pathIdxStart > pathIdxEnd) {
			// String is exhausted
			for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
				if (!pattDirs[i].equals("**")) {
					return false;
				}
			}
			return true;
		}

		while (pattIdxStart != pattIdxEnd && pathIdxStart <= pathIdxEnd) {
			int patIdxTmp = -1;
			for (int i = pattIdxStart + 1; i <= pattIdxEnd; i++) {
				if (pattDirs[i].equals("**")) {
					patIdxTmp = i;
					break;
				}
			}
			if (patIdxTmp == pattIdxStart + 1) {
				// '**/**' situation, so skip one
				pattIdxStart++;
				continue;
			}
			// Find the pattern between padIdxStart & padIdxTmp in str between
			// strIdxStart & strIdxEnd
			int patLength = (patIdxTmp - pattIdxStart - 1);
			int strLength = (pathIdxEnd - pathIdxStart + 1);
			int foundIdx = -1;

			strLoop:
			for (int i = 0; i <= strLength - patLength; i++) {
				for (int j = 0; j < patLength; j++) {
					String subPat = pattDirs[pattIdxStart + j + 1];
					String subStr = pathDirs[pathIdxStart + i + j];
					if (!matchStrings(subPat, subStr, uriTemplateVariables)) {
						continue strLoop;
					}
				}
				foundIdx = pathIdxStart + i;
				break;
			}

			if (foundIdx == -1) {
				return false;
			}

			pattIdxStart = patIdxTmp;
			pathIdxStart = foundIdx + patLength;
		}

		for (int i = pattIdxStart; i <= pattIdxEnd; i++) {
			if (!pattDirs[i].equals("**")) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Tests whether or not a string matches against a pattern. The pattern may contain two special characters:
	 * <br>'*' means zero or more characters
	 * <br>'?' means one and only one character
	 * @param pattern pattern to match against. Must not be {@code null}.
	 * @param str string which must be matched against the pattern. Must not be {@code null}.
	 * @return {@code true} if the string matches against the pattern, or {@code false} otherwise.
	 */
	private boolean matchStrings(String pattern, String str, Map<String, String> uriTemplateVariables) {
		AntPathStringMatcher matcher = this.stringMatcherCache.get(pattern);
		if (matcher == null) {
			matcher = new AntPathStringMatcher(pattern);
			this.stringMatcherCache.put(pattern, matcher);
		}
		return matcher.matchStrings(str, uriTemplateVariables);
	}
	
	/**
	 * Tokenize the given String into a String array via a StringTokenizer.
	 * <p>The given delimiters string is supposed to consist of any number of
	 * delimiter characters. Each of those characters can be used to separate
	 * tokens. A delimiter is always a single character; for multi-character
	 * delimiters, consider using {@code delimitedListToStringArray}
	 * @param str the String to tokenize
	 * @param delimiters the delimiter characters, assembled as String
	 * (each of those characters is individually considered as delimiter)
	 * @param trimTokens trim the tokens via String's {@code trim}
	 * @param ignoreEmptyTokens omit empty tokens from the result array
	 * (only applies to tokens that are empty after trimming; StringTokenizer
	 * will not consider subsequent delimiters as token in the first place).
	 * @return an array of the tokens ({@code null} if the input String
	 * was {@code null})
	 * @see java.util.StringTokenizer
	 * @see String#trim()
	 * @see #delimitedListToStringArray
	 */
	private static String[] tokenizeToStringArray(
			String str, String delimiters, boolean trimTokens, boolean ignoreEmptyTokens) {

		if (str == null) {
			return null;
		}
		StringTokenizer st = new StringTokenizer(str, delimiters);
		List<String> tokens = new LinkedList<String>();
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (trimTokens) {
				token = token.trim();
			}
			if (!ignoreEmptyTokens || token.length() > 0) {
				tokens.add(token);
			}
		}
		return tokens.toArray(new String[]{});
	}

	/**
	 * Tests whether or not a string matches against a pattern via a {@link Pattern}.
	 * <p>The pattern may contain special characters: '*' means zero or more characters; '?' means one and
	 * only one character; '{' and '}' indicate a URI template pattern. For example <tt>/users/{user}</tt>.
	 */
	private static class AntPathStringMatcher {

		private static final Pattern GLOB_PATTERN = Pattern.compile("\\?|\\*|\\{((?:\\{[^/]+?\\}|[^/{}]|\\\\[{}])+?)\\}");

		private static final String DEFAULT_VARIABLE_PATTERN = "(.*)";

		private final Pattern pattern;

		private final List<String> variableNames = new LinkedList<String>();

		public AntPathStringMatcher(String pattern) {
			StringBuilder patternBuilder = new StringBuilder();
			Matcher m = GLOB_PATTERN.matcher(pattern);
			int end = 0;
			while (m.find()) {
				patternBuilder.append(quote(pattern, end, m.start()));
				String match = m.group();
				if ("?".equals(match)) {
					patternBuilder.append('.');
				}
				else if ("*".equals(match)) {
					patternBuilder.append(".*");
				}
				else if (match.startsWith("{") && match.endsWith("}")) {
					int colonIdx = match.indexOf(':');
					if (colonIdx == -1) {
						patternBuilder.append(DEFAULT_VARIABLE_PATTERN);
						this.variableNames.add(m.group(1));
					}
					else {
						String variablePattern = match.substring(colonIdx + 1, match.length() - 1);
						patternBuilder.append('(');
						patternBuilder.append(variablePattern);
						patternBuilder.append(')');
						String variableName = match.substring(1, colonIdx);
						this.variableNames.add(variableName);
					}
				}
				end = m.end();
			}
			patternBuilder.append(quote(pattern, end, pattern.length()));
			this.pattern = Pattern.compile(patternBuilder.toString());
		}

		private String quote(String s, int start, int end) {
			if (start == end) {
				return "";
			}
			return Pattern.quote(s.substring(start, end));
		}

		/**
		 * Main entry point.
		 * @return {@code true} if the string matches against the pattern, or {@code false} otherwise.
		 */
		public boolean matchStrings(String str, Map<String, String> uriTemplateVariables) {
			Matcher matcher = this.pattern.matcher(str);
			if (matcher.matches()) {
				if (uriTemplateVariables != null) {
					Preconditions.checkArgument(
						this.variableNames.size() == matcher.groupCount(),
						"The number of capturing groups in the pattern " +
						"segment " + this.pattern + " does not match the " +
						"number of URI template variables it defines, which " +
						"can occur if capturing groups are used in a URI " +
						"template regex. Use non-capturing groups instead.");
					for (int i = 1; i <= matcher.groupCount(); i++) {
						String name = this.variableNames.get(i - 1);
						String value = matcher.group(i);
						uriTemplateVariables.put(name, value);
					}
				}
				return true;
			}
			else {
				return false;
			}
		}
		
	}

}
