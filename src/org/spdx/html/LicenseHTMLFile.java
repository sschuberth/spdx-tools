/**
 * Copyright (c) 2011 Source Auditor Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.spdx.html;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spdx.licenseTemplate.LicenseTemplateRuleException;
import org.spdx.licenseTemplate.SpdxLicenseTemplateHelper;
import org.spdx.rdfparser.license.SpdxListedLicense;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * This class contains a formatted HTML file for a given license.  Specific
 * formatting information is contained in this file.
 * @author Gary O'Neall
 *
 */
public class LicenseHTMLFile {
	
	static final String TEMPLATE_CLASS_PATH = "resources" + "/" + "htmlTemplate";
	static final String TEMPLATE_ROOT_PATH = "resources" + File.separator + "htmlTemplate";
	static final String TEMPLATE_FILE_NAME = "LicenseHTMLTemplate.html";
	static final boolean USE_SITE = false;	// set to true to use the site name for the link of external web pages

	static final Pattern SITE_PATTERN = Pattern.compile("http://(.*)\\.(.*)(\\.org|\\.com|\\.net|\\.info)");
	
	private boolean deprecated;
	private String deprecatedVersion;
	
	/**
	 * Parses a URL and stores the site name and the original URL
	 * @author Gary O'Neall
	 *
	 */
	public static class FormattedUrl {
		String url;
		public FormattedUrl(String url) {
			this.url = url;
		}
		public String getUrl() {
			return this.url;
		}
		public void setUrl(String url) {
			this.url = url;
		}
		public String getSite() {
			return getSiteFromUrl(url);
		}
		
		@SuppressWarnings("unused")
		private String getSiteFromUrl(String url) {
			Matcher matcher = SITE_PATTERN.matcher(url);
			if (matcher.find() && USE_SITE) {
				int numGroups = matcher.groupCount();
				return matcher.group(numGroups-1);
			} else {
				return url;
			}
		}
	}
	private SpdxListedLicense license;
	/**
	 * @param templateFileName File name for the Mustache template file
	 * @param license Listed license to be used
	 * @param isDeprecated True if the license has been deprecated
	 * @param deprecatedVersion Version since the license has been deprecated (null if not deprecated)
	 */
	public LicenseHTMLFile(SpdxListedLicense license,
			boolean isDeprecated, String deprecatedVersion) {
		this.license = license;
		this.deprecated = isDeprecated;
		this.deprecatedVersion = deprecatedVersion;
	}
	public LicenseHTMLFile() {
		this(null, false, null);
	}
	
	/**
	 * @return the license
	 */
	public SpdxListedLicense getLicense() {
		return license;
	}

	/**
	 * @param license the license to set
	 */
	public void setLicense(SpdxListedLicense license) {
		this.license = license;
	}

	/**
	 * @return the isDeprecated
	 */
	public boolean isDeprecated() {
		return deprecated;
	}
	/**
	 * @param isDeprecated the isDeprecated to set
	 */
	public void setDeprecated(boolean isDeprecated) {
		this.deprecated = isDeprecated;
	}
	/**
	 * @return the deprecatedVersion
	 */
	public String getDeprecatedVersion() {
		return deprecatedVersion;
	}
	/**
	 * @param deprecatedVersion the deprecatedVersion to set
	 */
	public void setDeprecatedVersion(String deprecatedVersion) {
		this.deprecatedVersion = deprecatedVersion;
	}
	
	public void writeToFile(File htmlFile, String tableOfContentsReference) throws IOException, MustacheException, InvalidLicenseTemplateException {
		FileOutputStream stream = null;
		OutputStreamWriter writer = null;
		if (!htmlFile.exists()) {
			if (!htmlFile.createNewFile()) {
				throw(new IOException("Can not create new file "+htmlFile.getName()));
			}
		}
		String templateDirName = TEMPLATE_ROOT_PATH;
		File templateDirectoryRoot = new File(templateDirName);
		if (!(templateDirectoryRoot.exists() && templateDirectoryRoot.isDirectory())) {
			templateDirName = TEMPLATE_CLASS_PATH;
		}
		try {
			stream = new FileOutputStream(htmlFile);
			writer = new OutputStreamWriter(stream, "UTF-8");
			DefaultMustacheFactory builder = new DefaultMustacheFactory(templateDirName);
	        Map<String, Object> mustacheMap = buildMustachMap();
	        Mustache mustache = builder.compile(TEMPLATE_FILE_NAME);
	        mustache.execute(writer, mustacheMap);
		} finally {
			if (writer != null) {
				writer.close();
			}
			if (stream != null) {
				stream.close();
			}
		}		
	}
	/**
	 * @return
	 * @throws LicenseTemplateRuleException 
	 */
	private Map<String, Object> buildMustachMap() throws InvalidLicenseTemplateException {
			Map<String, Object> retval = Maps.newHashMap();
			if (license != null) {
				retval.put("licenseId", license.getLicenseId());
				String licenseTextHtml = null;
				String licenseTemplateHtml = null;
				String templateText = license.getStandardLicenseTemplate();
				if (templateText != null && !templateText.trim().isEmpty()) {
					try {
						licenseTextHtml = formatTemplateText(templateText);
					} catch(LicenseTemplateRuleException ex) {
						throw new InvalidLicenseTemplateException("Invalid license expression found in license text for license "+this.license.getName()+":"+ex.getMessage());
					}
					licenseTemplateHtml = SpdxLicenseTemplateHelper.escapeHTML(templateText);
				} else {
					licenseTextHtml = SpdxLicenseTemplateHelper.escapeHTML(license.getLicenseText());
				}
				retval.put("licenseText", licenseTextHtml);
				if (licenseTemplateHtml != null) {
					try {
						retval.put("licenseTemplate", formatTemplateText(licenseTemplateHtml));
					} catch(LicenseTemplateRuleException ex) {
						throw new InvalidLicenseTemplateException("Invalid license expression found in license template text for license "+this.license.getName()+":"+ex.getMessage());
					}
				}
				retval.put("licenseName", license.getName());
				String notes;
				if (license.getComment() != null && !license.getComment().isEmpty()) {
					notes = license.getComment();
				} else {
					notes = "None";
				}
				retval.put("notes", notes);
				retval.put("osiApproved", license.isOsiApproved());
				List<FormattedUrl> otherWebPages = Lists.newArrayList();
				if (license.getSeeAlso() != null && license.getSeeAlso().length > 0) {
					for (String sourceUrl : license.getSeeAlso()) {
						if (sourceUrl != null && !sourceUrl.isEmpty()) {
							FormattedUrl formattedUrl = new FormattedUrl(sourceUrl);
							otherWebPages.add(formattedUrl);
						}
				}
				if (otherWebPages.size() == 0) {
					otherWebPages = null;	// Force the template to print None
				}
				retval.put("otherWebPages", otherWebPages);
				retval.put("title", license.getName());
				String header = license.getStandardLicenseHeader();
				if (header != null && header.trim().isEmpty()) {
					header = null;	// so the template will appropriately skip the header text
				} else {
					//TODO: May need to add another field for the header template text
					try {
						header = formatTemplateText(header);
					} catch(LicenseTemplateRuleException ex) {
						throw new InvalidLicenseTemplateException("Invalid license expression found in header text for license "+this.license.getName()+":"+ex.getMessage());
					}
				}
				retval.put("licenseHeader", header);
			}
		}
		retval.put("deprecated", this.isDeprecated());
		retval.put("deprecatedVersion", this.deprecatedVersion);
		return retval;
	}
	/**
	 * Formats the license text from a template
	 * @param licenseTemplate
	 * @return
	 * @throws LicenseTemplateRuleException 
	 */
	private String formatTemplateText(String licenseTemplate) throws LicenseTemplateRuleException {
		return SpdxLicenseTemplateHelper.templateTextToHtml(licenseTemplate);
	}
}
