package com.chinacreator.gzcm.runtime.core.format.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import com.chinacreator.gzcm.runtime.core.format.Format;
import com.chinacreator.gzcm.runtime.core.format.FormatException;
import com.chinacreator.gzcm.runtime.core.format.FormatValidator;
import com.chinacreator.gzcm.runtime.core.format.model.FormatMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * 格式验证器实现
 * 
 * @author CDRC Runtime Team
 */
public class FormatValidatorImpl implements FormatValidator {
    
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final XmlMapper XML_MAPPER = new XmlMapper();
    
    @Override
    public ValidationResult validate(InputStream input, Format format, FormatMetadata metadata) 
            throws FormatException {
        List<String> errors = new ArrayList<>();
        
        try {
            if (input == null) {
                errors.add("Input stream is null");
                return new ValidationResult(false, "Validation failed", errors);
            }
            
            if (input.available() == 0) {
                errors.add("Input stream is empty");
                return new ValidationResult(false, "Validation failed", errors);
            }
            
            // 根据格式进行验证
            if (format == Format.CSV) {
                return validateCsv(input, metadata, errors);
            } else if (format == Format.JSON) {
                return validateJson(input, errors);
            } else if (format == Format.XML) {
                return validateXml(input, errors);
            } else {
                errors.add("Unsupported format: " + format);
                return new ValidationResult(false, "Unsupported format", errors);
            }
        } catch (Exception e) {
            errors.add("Validation error: " + e.getMessage());
            return new ValidationResult(false, "Validation failed with exception", errors);
        }
    }
    
    private ValidationResult validateCsv(InputStream input, FormatMetadata metadata, List<String> errors) 
            throws Exception {
        Charset encoding = Charset.forName(metadata.getEncoding());
        String delimiter = metadata.getDelimiter() != null ? metadata.getDelimiter() : ",";
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding))) {
            String line;
            int lineNum = 0;
            int expectedColumns = -1;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                String[] columns = line.split(delimiter, -1);
                
                if (expectedColumns == -1) {
                    expectedColumns = columns.length;
                } else if (columns.length != expectedColumns) {
                    errors.add(String.format("Line %d has %d columns, expected %d", 
                        lineNum, columns.length, expectedColumns));
                }
            }
            
            if (lineNum == 0) {
                errors.add("CSV file is empty");
            }
        }
        
        if (errors.isEmpty()) {
            return new ValidationResult(true, "CSV format is valid");
        } else {
            return new ValidationResult(false, "CSV validation failed", errors);
        }
    }
    
    private ValidationResult validateJson(InputStream input, List<String> errors) {
        try {
            JSON_MAPPER.readTree(input);
            return new ValidationResult(true, "JSON format is valid");
        } catch (Exception e) {
            errors.add("Invalid JSON: " + e.getMessage());
            return new ValidationResult(false, "JSON validation failed", errors);
        }
    }
    
    private ValidationResult validateXml(InputStream input, List<String> errors) {
        try {
            XML_MAPPER.readTree(input);
            return new ValidationResult(true, "XML format is valid");
        } catch (Exception e) {
            errors.add("Invalid XML: " + e.getMessage());
            return new ValidationResult(false, "XML validation failed", errors);
        }
    }
}

