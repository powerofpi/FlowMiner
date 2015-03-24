package edu.iastate.flowminer.io.common;

/**
 * Lightweight XML escaping form Atlas. Borrowed at suggestion of Nikhil.
 * 
 * @author tdeering
 *
 */
public class XMLUtils {
	/** 
	 *  TODO: Writers do not handle supplementary characters; they
	 *  are character based.
	 *  <p>
	 *  Invalid characters are replaced with ? to avoid forcing
	 *  clients to catch exceptions.  This is analagous to the 
	 *  behavior taken by Java's character encoders.
	 *  
	 *  @return the String s, with [<>\"&] correctly escaped, and
	 *  invalid characters replaced with "?".
	 *  <p>
	 * @throws IOException 
	 **/
	public static String writeValidXMLText(String s) throws IOException {
		
		StringBuilder sb = new StringBuilder(s.length());
		
		// the characters < > ' " & must be escaped - 
		// the rest are fine
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '<':
					sb.append("&lt;");
					break;
				case '>':
					sb.append("&gt;");
					break;
				case '\'':
					sb.append("&apos;");
					break;
				case '"':
					sb.append("&quot;");
					break;
				case '&':
					sb.append("&amp;");
					break;
					
				/*
				 * Escape newlines and tabs.  Technically this is not required by the standard,
				 * but SAXParser maps raw newlines and tabs to spaces.
				 */
				case '\n':
					sb.append("&#010;");
					break;
				case '\t':
					sb.append("&#009;");
					break;
				default:
					if (isValidCodePoint(c)) {
						// isValidCodePoint will reject the surrogates until
						// c is replaced by a codepoint: c = s.codePointAt(i)
						sb.append(c);
					} else {
						sb.append("?");
					}
			}
		}
		
		return sb.toString();
	}
	
	/**
	 * 
	 * @param codePoint
	 * @return true if codePoint is a valid XML character
	 * 
	 */
	private static boolean isValidCodePoint(int codePoint) {

		/*
		 * Allowable XML characters inlcude (these are Unicode code points): #x9
		 * | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
		 * (any Unicode character, excluding the surrogate blocks, FFFE, and
		 * FFFF)
		 * 
		 * 
		 * There are "compatibility characters" which should be avoided becuase
		 * they are control characters or undefined, but they do not appear to
		 * be strictly illegal:
		 * 
		 * [#x7F-#x84], [#x86-#x9F], [#xFDD0-#xFDDF], [#x1FFFE-#x1FFFF],
		 * [#x2FFFE-#x2FFFF], [#x3FFFE-#x3FFFF], [#x4FFFE-#x4FFFF],
		 * [#x5FFFE-#x5FFFF], [#x6FFFE-#x6FFFF], [#x7FFFE-#x7FFFF],
		 * [#x8FFFE-#x8FFFF], [#x9FFFE-#x9FFFF], [#xAFFFE-#xAFFFF],
		 * [#xBFFFE-#xBFFFF], [#xCFFFE-#xCFFFF], [#xDFFFE-#xDFFFF],
		 * [#xEFFFE-#xEFFFF], [#xFFFFE-#xFFFFF], [#x10FFFE-#x10FFFF]
		 * 
		 * We treat them as invalid because JClark does.
		 * 
		 * NOTE: the XMLWriter was tested for performance after implementing
		 * this check. The test involved writing 1M attributes of about 30
		 * characters each. The overhead caused by this method appeared to be
		 * negligible.
		 */

		// exclude control characters
		if (codePoint < 0x20) {
			// but allow tab, newline, and linefeed
			if (codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD)
				return true;
			return false;
		}

		// remaining: codePoint >= 0x20

		if (codePoint <= 0xD7FF) {
			// exclude control characters
			if (codePoint >= 0x7F && codePoint <= 0x9F && codePoint != 0x85)
				return false;
			return true;
		}

		// remaining: codePoint >= 0xD800

		if (codePoint <= 0xDFFF)
			return false;

		// exclude code points which are out of range
		if (codePoint > 0x10FFFF)
			return false;

		// remaining are: codePoint >= 0xE000 && codePoint <= 0x10FFFF

		// exclude 0x0FFF[EF] - 0x10FFF[EF]
		if ((codePoint & 0xFFFE) == 0xFFFE) {
			return false;
		}

		// exclude 0xFFD0-0xFFDF
		if ((codePoint & 0xFFFFF0) == 0xFDD0)
			return false;

		return true;
	}
}
