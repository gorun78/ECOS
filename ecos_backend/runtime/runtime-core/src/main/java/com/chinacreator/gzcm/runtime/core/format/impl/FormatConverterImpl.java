package com.chinacreator.gzcm.runtime.core.format.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.format.Format;
import com.chinacreator.gzcm.runtime.core.format.FormatConverter;
import com.chinacreator.gzcm.runtime.core.format.FormatException;
import com.chinacreator.gzcm.runtime.core.format.model.FormatContext;
import com.chinacreator.gzcm.runtime.core.format.model.FormatMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * 格式转换器实现
 * 支持CSV、JSON、XML等格式的转换
 * 
 * @author CDRC Runtime Team
 */
public class FormatConverterImpl implements FormatConverter {
    
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final XmlMapper XML_MAPPER = new XmlMapper();
    
    @Override
    public <T> List<T> read(InputStream input, Format sourceFormat, Class<T> clazz, FormatContext context) 
            throws FormatException {
        try {
            if (sourceFormat == Format.CSV) {
                return readCsv(input, clazz, context);
            } else if (sourceFormat == Format.JSON) {
                return readJson(input, clazz, context);
            } else if (sourceFormat == Format.XML) {
                return readXml(input, clazz, context);
            } else {
                throw new FormatException("Unsupported format for reading: " + sourceFormat);
            }
        } catch (Exception e) {
            throw new FormatException("Failed to read data from " + sourceFormat, e);
        }
    }
    
    @Override
    public void write(List<?> data, OutputStream output, Format targetFormat, FormatContext context) 
            throws FormatException {
        try {
            if (targetFormat == Format.CSV) {
                writeCsv(data, output, context);
            } else if (targetFormat == Format.JSON) {
                writeJson(data, output, context);
            } else if (targetFormat == Format.XML) {
                writeXml(data, output, context);
            } else {
                throw new FormatException("Unsupported format for writing: " + targetFormat);
            }
        } catch (Exception e) {
            throw new FormatException("Failed to write data to " + targetFormat, e);
        }
    }
    
    @Override
    public void convert(InputStream input, Format sourceFormat, OutputStream output, Format targetFormat,
                        FormatContext sourceContext, FormatContext targetContext) throws FormatException {
        try {
            // 先读取为通用对象列表
            List<Map<String, Object>> data = readAsMap(input, sourceFormat, sourceContext);
            // 再写入目标格式
            writeMap(data, output, targetFormat, targetContext);
        } catch (Exception e) {
            throw new FormatException("Failed to convert from " + sourceFormat + " to " + targetFormat, e);
        }
    }
    
    @Override
    public boolean validate(InputStream input, Format format, FormatMetadata metadata) throws FormatException {
        try {
            // 基本验证：检查输入流是否可读
            if (input == null || input.available() == 0) {
                return false;
            }
            
            // 根据格式进行基本验证
            if (format == Format.CSV) {
                return validateCsv(input, metadata);
            } else if (format == Format.JSON) {
                return validateJson(input);
            } else if (format == Format.XML) {
                return validateXml(input);
            }
            
            return true;
        } catch (Exception e) {
            throw new FormatException("Failed to validate format " + format, e);
        }
    }
    
    @Override
    public List<Format> getSupportedFormats() {
        return Arrays.asList(Format.CSV, Format.JSON, Format.XML);
    }
    
    @Override
    public boolean supports(Format format) {
        return format == Format.CSV || format == Format.JSON || format == Format.XML;
    }
    
    // ==================== CSV 处理 ====================
    
    @SuppressWarnings("unchecked")
    private <T> List<T> readCsv(InputStream input, Class<T> clazz, FormatContext context) throws Exception {
        Charset encoding = context.getEncoding();
        String delimiter = context.getDelimiter();
        boolean hasHeader = context.getHasHeader();
        Character quoteChar = context.getQuoteChar();
        
        List<T> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding))) {
            String line;
            String[] headers = null;
            int lineNum = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String[] values = parseCsvLine(line, delimiter, quoteChar);
                
                if (hasHeader && lineNum == 1) {
                    headers = values;
                    continue;
                }
                
                if (clazz == Map.class || clazz == LinkedHashMap.class) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    if (headers != null) {
                        for (int i = 0; i < headers.length && i < values.length; i++) {
                            map.put(headers[i], values[i]);
                        }
                    } else {
                        for (int i = 0; i < values.length; i++) {
                            map.put("column" + i, values[i]);
                        }
                    }
                    result.add((T) map);
                } else {
                    // 对于其他类型，尝试使用JSON映射
                    Map<String, Object> map = new LinkedHashMap<>();
                    if (headers != null) {
                        for (int i = 0; i < headers.length && i < values.length; i++) {
                            map.put(headers[i], values[i]);
                        }
                    }
                    T obj = JSON_MAPPER.convertValue(map, clazz);
                    result.add(obj);
                }
            }
        }
        
        return result;
    }
    
    private void writeCsv(List<?> data, OutputStream output, FormatContext context) throws Exception {
        Charset encoding = context.getEncoding();
        String delimiter = context.getDelimiter();
        boolean hasHeader = context.getHasHeader();
        Character quoteChar = context.getQuoteChar();
        
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, encoding))) {
            if (data.isEmpty()) {
                return;
            }
            
            Object first = data.get(0);
            Map<String, Object> firstMap = convertToMap(first);
            
            // 写入表头
            if (hasHeader && firstMap != null) {
                String[] headers = firstMap.keySet().toArray(new String[0]);
                writer.println(String.join(delimiter, headers));
            }
            
            // 写入数据
            for (Object item : data) {
                Map<String, Object> map = convertToMap(item);
                if (map != null) {
                    List<String> values = new ArrayList<>();
                    for (Object value : map.values()) {
                        String str = value != null ? value.toString() : "";
                        if (quoteChar != null && (str.contains(delimiter) || str.contains("\n"))) {
                            String quoteStr = String.valueOf(quoteChar);
                            str = quoteChar + str.replace(quoteStr, quoteStr + quoteStr) + quoteChar;
                        }
                        values.add(str);
                    }
                    writer.println(String.join(delimiter, values));
                }
            }
        }
    }
    
    private boolean validateCsv(InputStream input, FormatMetadata metadata) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line = reader.readLine();
            return line != null && !line.trim().isEmpty();
        }
    }
    
    private String[] parseCsvLine(String line, String delimiter, Character quoteChar) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == quoteChar && inQuotes) {
                if (i + 1 < line.length() && line.charAt(i + 1) == quoteChar) {
                    current.append(quoteChar);
                    i++;
                } else {
                    inQuotes = false;
                }
            } else if (c == quoteChar) {
                inQuotes = true;
            } else if (c == delimiter.charAt(0) && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        
        return fields.toArray(new String[0]);
    }
    
    // ==================== JSON 处理 ====================
    
    @SuppressWarnings("unchecked")
    private <T> List<T> readJson(InputStream input, Class<T> clazz, FormatContext context) throws Exception {
        Charset encoding = context.getEncoding();
        byte[] bytes = readAllBytes(input);
        String content = new String(bytes, encoding);
        
        if (clazz == Map.class || clazz == LinkedHashMap.class) {
            Object obj = JSON_MAPPER.readValue(content, Object.class);
            if (obj instanceof List) {
                return (List<T>) obj;
            } else {
                List<T> result = new ArrayList<>();
                result.add((T) obj);
                return result;
            }
        } else {
            return JSON_MAPPER.readValue(content, 
                JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        }
    }
    
    private void writeJson(List<?> data, OutputStream output, FormatContext context) throws Exception {
        Charset encoding = context.getEncoding();
        String json = JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        output.write(json.getBytes(encoding));
    }
    
    private boolean validateJson(InputStream input) throws Exception {
        try {
            JSON_MAPPER.readTree(input);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    // ==================== XML 处理 ====================
    
    @SuppressWarnings("unchecked")
    private <T> List<T> readXml(InputStream input, Class<T> clazz, FormatContext context) throws Exception {
        // XML读取需要特定的根元素结构，这里简化处理
        Charset encoding = context.getEncoding();
        byte[] bytes = readAllBytes(input);
        String content = new String(bytes, encoding);
        
        if (clazz == Map.class || clazz == LinkedHashMap.class) {
            Map<String, Object> map = XML_MAPPER.readValue(content, Map.class);
            List<T> result = new ArrayList<>();
            result.add((T) map);
            return result;
        } else {
            T obj = XML_MAPPER.readValue(content, clazz);
            List<T> result = new ArrayList<>();
            result.add(obj);
            return result;
        }
    }
    
    private void writeXml(List<?> data, OutputStream output, FormatContext context) throws Exception {
        Charset encoding = context.getEncoding();
        if (data.size() == 1) {
            String xml = XML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data.get(0));
            output.write(xml.getBytes(encoding));
        } else {
            // 多个对象需要包装在根元素中
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("items", data);
            String xml = XML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            output.write(xml.getBytes(encoding));
        }
    }
    
    private boolean validateXml(InputStream input) throws Exception {
        try {
            XML_MAPPER.readTree(input);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    // ==================== 辅助方法 ====================
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readAsMap(InputStream input, Format sourceFormat, FormatContext context) 
            throws Exception {
        List<Map> rawList = (List<Map>) read(input, sourceFormat, Map.class, context);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map map : rawList) {
            result.add((Map<String, Object>) map);
        }
        return result;
    }
    
    private void writeMap(List<Map<String, Object>> data, OutputStream output, Format targetFormat, 
                          FormatContext context) throws Exception {
        write((List<?>) (List<?>) data, output, targetFormat, context);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        } else {
            return JSON_MAPPER.convertValue(obj, Map.class);
        }
    }
    
    // Java 8兼容方法：读取所有字节
    private byte[] readAllBytes(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = input.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}

