package com.chinacreator.gzcm.runtime.core.format.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.chinacreator.gzcm.runtime.core.format.Format;
import com.chinacreator.gzcm.runtime.core.format.model.FormatContext;

/**
 * FormatConverterImpl 单元测试
 */
@DisplayName("格式转换服务测试")
class FormatConverterImplTest {

    private FormatConverterImpl formatConverter;
    private FormatContext defaultContext;

    @BeforeEach
    void setUp() {
        formatConverter = new FormatConverterImpl();
        defaultContext = new FormatContext();
        defaultContext.setEncoding(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("JSON转XML")
    void testConvertJsonToXml() throws Exception {
        String json = "{\"name\":\"test\",\"value\":123}";
        InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        formatConverter.convert(input, Format.JSON, output, Format.XML, defaultContext, defaultContext);
        
        String xml = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertNotNull(xml);
        assertTrue(xml.length() > 0);
    }

    @Test
    @DisplayName("XML转JSON")
    void testConvertXmlToJson() throws Exception {
        String xml = "<root><name>test</name><value>123</value></root>";
        InputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        formatConverter.convert(input, Format.XML, output, Format.JSON, defaultContext, defaultContext);
        
        String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertNotNull(json);
        assertTrue(json.length() > 0);
    }

    @Test
    @DisplayName("CSV转JSON")
    void testConvertCsvToJson() throws Exception {
        String csv = "name,value\ntest,123";
        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FormatContext csvContext = new FormatContext();
        csvContext.setEncoding(StandardCharsets.UTF_8);
        csvContext.setDelimiter(",");
        csvContext.setHasHeader(true);

        formatConverter.convert(input, Format.CSV, output, Format.JSON, csvContext, defaultContext);
        
        String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertNotNull(json);
        assertTrue(json.length() > 0);
    }

    @Test
    @DisplayName("JSON转CSV")
    void testConvertJsonToCsv() throws Exception {
        String json = "[{\"name\":\"test\",\"value\":123}]";
        InputStream input = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FormatContext csvContext = new FormatContext();
        csvContext.setEncoding(StandardCharsets.UTF_8);
        csvContext.setDelimiter(",");
        csvContext.setHasHeader(true);

        formatConverter.convert(input, Format.JSON, output, Format.CSV, defaultContext, csvContext);
        
        String csv = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertNotNull(csv);
        assertTrue(csv.length() > 0);
    }

    @Test
    @DisplayName("检查是否支持格式")
    void testSupports() {
        assertTrue(formatConverter.supports(Format.JSON));
        assertTrue(formatConverter.supports(Format.XML));
        assertTrue(formatConverter.supports(Format.CSV));
    }

    @Test
    @DisplayName("获取支持的格式列表")
    void testGetSupportedFormats() {
        var formats = formatConverter.getSupportedFormats();
        assertNotNull(formats);
        assertTrue(formats.size() > 0);
        assertTrue(formats.contains(Format.JSON));
        assertTrue(formats.contains(Format.XML));
        assertTrue(formats.contains(Format.CSV));
    }
}

