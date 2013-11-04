/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.kenyaemr.page.controller;

import org.apache.commons.io.FileUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyacore.UiResource;
import org.openmrs.module.kenyacore.report.IndicatorReportDescriptor;
import org.openmrs.module.kenyacore.report.ReportDescriptor;
import org.openmrs.module.kenyacore.report.ReportManager;
import org.openmrs.module.kenyaemr.util.EmrUiUtils;
import org.openmrs.module.kenyaui.annotation.SharedPage;
import org.openmrs.module.reporting.common.ContentType;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.report.ReportData;
import org.openmrs.module.reporting.report.ReportDesign;
import org.openmrs.module.reporting.report.ReportDesignResource;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.definition.service.ReportDefinitionService;
import org.openmrs.module.reporting.report.renderer.CsvReportRenderer;
import org.openmrs.module.reporting.report.renderer.ExcelTemplateRenderer;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.FileDownload;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.page.PageRequest;
import org.openmrs.ui.framework.resource.ResourceFactory;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Download report as Excel or CSV page
 */
@SharedPage
public class ReportExportPageController {

	public FileDownload get(@RequestParam("request") ReportRequest reportRequest,
							@RequestParam("type") String type,
					PageRequest pageRequest,
					@SpringBean ReportManager reportManager,
					@SpringBean EmrUiUtils emrUi,
					@SpringBean ResourceFactory resourceFactory,
					@SpringBean ReportService reportService) throws Exception {

		ReportDefinition definition = reportRequest.getReportDefinition().getParameterizable();
		ReportDescriptor report = reportManager.getReportByDefinition(definition);
		emrUi.checkReportAccess(pageRequest, report);

		ReportData reportData = reportService.loadReportData(reportRequest);

		if (type.equals("excel")) {
			UiResource template = ((IndicatorReportDescriptor) report).getTemplate();

			if (template == null || !template.getPath().endsWith(".xls")) {
				throw new IllegalArgumentException("Report doesn't specify a Excel template");
			}

			// Load report template
			byte[] templateData = loadTemplateResource(resourceFactory, template);

			return renderAsExcel(definition, reportData, templateData);
		}
		else if (type.equals("csv")) {
			return renderAsCsv(definition, reportData);
		}
		else {
			throw new RuntimeException("Unrecognised export type: " + type);
		}
	}

	/**
	 * Renders an indicator report as Excel
	 * @param definition the report definition
	 * @param data the evaluated report data
	 * @return the Excel file as a download
	 * @throws IOException
	 */
	protected FileDownload renderAsExcel(ReportDefinition definition,
										 ReportData data,
										 byte[] templateData) throws IOException {

		ExcelTemplateRenderer renderer;
		{
			// this is a bit of a hack, copied from ExcelRendererTest in the reporting module, to avoid
			// needing to save the template and report design in the database
			ReportDesignResource resource = new ReportDesignResource();
			resource.setName("template.xls");
			resource.setContents(templateData);

			final ReportDesign design = new ReportDesign();
			design.setName(definition.getName() + " design");
			design.setReportDefinition(definition);
			design.setRendererType(ExcelTemplateRenderer.class);
			design.addResource(resource);

			renderer = new ExcelTemplateRenderer() {
				public ReportDesign getDesign(String argument) {
					return design;
				}
			};
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		renderer.render(data, "xxx:xls", out);

		return new FileDownload(
				getDownloadFilename(definition, data.getContext(), "xls"),
				ContentType.EXCEL.getContentType(),
				out.toByteArray()
		);
	}

	/**
	 * Renders an indicator report as CSV
	 * @param definition the report definition
	 * @param data the evaluated report data
	 * @return the file as a download
	 * @throws IOException
	 */
	protected FileDownload renderAsCsv(ReportDefinition definition, ReportData data) throws IOException {
		CsvReportRenderer renderer = new CsvReportRenderer();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		renderer.render(data, "xxx:csv", out);

		return new FileDownload(getDownloadFilename(definition, data.getContext(), "csv"), ContentType.CSV.getContentType(), out.toByteArray());
	}

	/**
	 * Loads a template resource as a byte array
	 * @param resourceFactory the resource factory
	 * @param template the template resource
	 * @return the byte array
	 * @throws IOException if resource couldn't be loaded
	 */
	protected byte[] loadTemplateResource(ResourceFactory resourceFactory, UiResource template) throws IOException {
		File file = resourceFactory.getResource(template.getProvider(), "reports/" + template.getPath());
		return FileUtils.readFileToByteArray(file);
	}

	/**
	 * Gets the filename to use for downloads
	 * @param ec the evaluation context
	 * @return the filename
	 */
	public String getDownloadFilename(ReportDefinition definition, EvaluationContext ec, String extension) {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM");
		return definition.getName() + " " + df.format(ec.getParameterValue("startDate")) + "." + extension;
	}
}