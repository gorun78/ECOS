import os
from typing import Dict, List, Any
from utils import (
    ensure_directory,
    generate_timestamp
)


class IntegrationTestGenerator:
    """集成测试生成器：生成前端(vue3)和后端(Java)的集成测试代码"""
    
    def __init__(self, output_dir: str = './tests'):
        self.output_dir = output_dir
        ensure_directory(output_dir)
    
    def generate_frontend_integration_tests(self, api_endpoints: List[Dict[str, Any]], 
                                          frontend_path: str = 'src') -> List[str]:
        """生成前端集成测试代码（Vue3 + Vitest）"""
        test_files = []
        
        for endpoint in api_endpoints:
            test_file = self._generate_vue_integration_test(endpoint)
            file_path = os.path.join(
                self.output_dir, 
                'frontend', 
                f"{endpoint['operationId']}.spec.ts"
            )
            ensure_directory(os.path.dirname(file_path))
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(test_file)
            test_files.append(file_path)
        
        return test_files
    
    def _generate_vue_integration_test(self, endpoint: Dict[str, Any]) -> str:
        """生成单个 Vue 集成测试文件"""
        method = endpoint.get('method', 'GET').upper()
        path = endpoint.get('path', '')
        operation_id = endpoint.get('operationId', '')
        request_body = endpoint.get('requestBody', {})
        response = endpoint.get('response', {})
        
        test_content = f"""import {{ describe, it, expect, beforeEach, afterEach, vi }} from 'vitest'
import {{ mount }} from '@vue/test-utils'
import {{ createRouter, createMemoryHistory }} from 'vue-router'
import {{ createPinia }} from 'pinia'
import {{ AxiosAdapter }} from './axios-adapter'

// Mock API 调用
vi.mock('axios', () => ({{
  default: {{
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn()
  }}
}}))

describe('{operation_id} Integration Test', () => {{
  let router: any
  let pinia: any

  beforeEach(() => {{
    router = createRouter({{
      history: createMemoryHistory(),
      routes: []
    }})
    pinia = createPinia()
  }})

  afterEach(() => {{
    vi.clearAllMocks()
  }})

  it('should handle {method} {path} correctly', async () => {{
    // Mock response
    const mockResponse = {{
      data: {self._format_response_data(response)}
    }}

    // Setup mock
    axios.{method.toLowerCase()}.mockResolvedValue(mockResponse)

    // Execute API call
    const result = await AxiosAdapter.{method.toLowerCase()}(
      '{path}',
      {self._format_request_data(request_body)}
    )

    // Assertions
    expect(result).toEqual(mockResponse.data)
    expect(axios.{method.toLowerCase()}).toHaveBeenCalledWith(
      '{path}',
      expect.any(Object)
    )
  }})

  it('should handle {method} {path} error', async () => {{
    // Mock error
    const mockError = new Error('API Error')
    axios.{method.toLowerCase()}.mockRejectedValue(mockError)

    // Execute API call and expect error
    await expect(
      AxiosAdapter.{method.toLowerCase()}(
        '{path}',
        {self._format_request_data(request_body)}
      )
    ).rejects.toThrow('API Error')
  }})
}})
"""
        return test_content
    
    def _format_response_data(self, response: Dict[str, Any]) -> str:
        """格式化响应数据"""
        if not response:
            return '{}'
        return str(response).replace("'", '"')
    
    def _format_request_data(self, request_body: Dict[str, Any]) -> str:
        """格式化请求数据"""
        if not request_body:
            return '{}'
        return str(request_body).replace("'", '"')
    
    def generate_backend_integration_tests(self, api_endpoints: List[Dict[str, Any]],
                                          backend_path: str = 'src/main/java') -> List[str]:
        """生成后端集成测试代码（Java + JUnit 5）"""
        test_files = []
        
        for endpoint in api_endpoints:
            test_file = self._generate_java_integration_test(endpoint)
            file_path = os.path.join(
                self.output_dir, 
                'backend', 
                f"{endpoint['operationId']}IntegrationTest.java"
            )
            ensure_directory(os.path.dirname(file_path))
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(test_file)
            test_files.append(file_path)
        
        return test_files
    
    def _generate_java_integration_test(self, endpoint: Dict[str, Any]) -> str:
        """生成单个 Java 集成测试文件"""
        method = endpoint.get('method', 'GET').upper()
        path = endpoint.get('path', '')
        operation_id = endpoint.get('operationId', '')
        controller_name = endpoint.get('controller', 'Controller')
        response = endpoint.get('response', {})
        
        test_content = f"""package com.chinacreator.ai.nativex.factory.controller;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {operation_id} 集成测试
 * 测试 {method} {path} 接口的端到端行为
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class {operation_id}IntegrationTest {{

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void test{operation_id}Success() throws Exception {{
        // 构建请求
        MvcResult result = mockMvc.perform({method.toLowerCase()}("{path}")
                        .contentType(MediaType.APPLICATION_JSON)
                        {self._generate_request_content(endpoint)})
                .andExpect(status().is{self._get_http_status(method)})
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                {self._generate_response_matchers(response)}
                .andReturn();

        // 验证响应内容
        String responseBody = result.getResponse().getContentAsString();
        Assertions.assertNotNull(responseBody);
    }}

    @Test
    @Order(2)
    void test{operation_id}ValidationError() throws Exception {{
        // 测试参数校验失败场景
        mockMvc.perform({method.toLowerCase()}("{path}")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{{}}"))
                .andExpect(status().isBadRequest());
    }}
}}
"""
        return test_content
    
    def _generate_request_content(self, endpoint: Dict[str, Any]) -> str:
        """生成请求内容"""
        method = endpoint.get('method', 'GET').upper()
        request_body = endpoint.get('requestBody', {})
        
        if method in ['POST', 'PUT', 'PATCH'] and request_body:
            props = request_body.get('properties', {})
            example = "{"
            for key, value in list(props.items())[:3]:
                value_type = value.get('type', 'string')
                if value_type == 'string':
                    example += f'"{key}": "test_{key}", '
                elif value_type == 'integer':
                    example += f'"{key}": 1, '
                elif value_type == 'boolean':
                    example += f'"{key}": true, '
            example = example.rstrip(', ') + "}"
            return f"\n                        .content(\"{example}\")"
        
        return ""
    
    def _get_http_status(self, method: str) -> str:
        """根据 HTTP 方法获取预期状态码"""
        status_map = {
            'GET': 'Ok',
            'POST': 'Created',
            'PUT': 'Ok',
            'DELETE': 'NoContent',
            'PATCH': 'Ok'
        }
        return status_map.get(method, 'Ok')
    
    def _generate_response_matchers(self, response: Dict[str, Any]) -> str:
        """生成响应断言"""
        if not response:
            return ""
        
        props = response.get('properties', {})
        matchers = ""
        
        for key in list(props.keys())[:3]:
            matchers += f"\n                .andExpect(jsonPath(\"$.{key}\").exists())"
        
        return matchers
    
    def generate_api_contract_tests(self, api_endpoints: List[Dict[str, Any]]) -> List[str]:
        """生成 API 契约测试（验证前后端接口一致性）"""
        test_files = []
        
        # 生成契约测试配置文件
        contract_config = self._generate_contract_config(api_endpoints)
        config_path = os.path.join(self.output_dir, 'contract', 'api-contracts.json')
        ensure_directory(os.path.dirname(config_path))
        with open(config_path, 'w', encoding='utf-8') as f:
            f.write(contract_config)
        test_files.append(config_path)
        
        # 生成契约测试脚本
        contract_test = self._generate_contract_test(api_endpoints)
        test_path = os.path.join(self.output_dir, 'contract', 'api-contract.test.ts')
        with open(test_path, 'w', encoding='utf-8') as f:
            f.write(contract_test)
        test_files.append(test_path)
        
        return test_files
    
    def _generate_contract_config(self, api_endpoints: List[Dict[str, Any]]) -> str:
        """生成契约测试配置"""
        config = {
            'version': '1.0.0',
            'timestamp': generate_timestamp(),
            'endpoints': []
        }
        
        for endpoint in api_endpoints:
            config['endpoints'].append({
                'method': endpoint.get('method', 'GET').upper(),
                'path': endpoint.get('path', ''),
                'operationId': endpoint.get('operationId', ''),
                'request': {
                    'contentType': 'application/json',
                    'schema': endpoint.get('requestBody', {})
                },
                'response': {
                    'statusCode': self._get_status_code(endpoint.get('method', 'GET')),
                    'contentType': 'application/json',
                    'schema': endpoint.get('response', {})
                }
            })
        
        import json
        return json.dumps(config, indent=2, ensure_ascii=False)
    
    def _get_status_code(self, method: str) -> int:
        """获取状态码"""
        status_map = {
            'GET': 200,
            'POST': 201,
            'PUT': 200,
            'DELETE': 204,
            'PATCH': 200
        }
        return status_map.get(method.upper(), 200)
    
    def _generate_contract_test(self, api_endpoints: List[Dict[str, Any]]) -> str:
        """生成契约测试脚本"""
        test_content = """import { describe, it, expect } from 'vitest'
import axios from 'axios'
import contracts from './api-contracts.json'

describe('API Contract Tests', () => {
  const baseUrl = process.env.VITE_API_BASE_URL || 'http://localhost:8080'

  contracts.endpoints.forEach((contract: any) => {
    describe(`${contract.method} ${contract.path}`, () => {
      it('should match contract schema', async () => {
        try {
          const response = await axios({
            method: contract.method.toLowerCase(),
            url: `${baseUrl}${contract.path}`,
            data: contract.request.schema.properties ? {} : undefined,
            validateStatus: () => true
          })

          // Validate status code
          expect(response.status).toBe(contract.response.statusCode)

          // Validate response structure
          if (contract.response.schema.properties) {
            const responseData = response.data
            const expectedProperties = Object.keys(contract.response.schema.properties)
            
            expectedProperties.forEach((prop) => {
              expect(responseData).toHaveProperty(prop)
            })
          }
        } catch (error) {
          console.error(`Contract test failed for ${contract.method} ${contract.path}:`, error)
          throw error
        }
      })
    })
  })
})
"""
        return test_content
    
    def generate_test_summary(self, frontend_tests: List[str], 
                             backend_tests: List[str],
                             contract_tests: List[str]) -> Dict[str, Any]:
        """生成测试摘要"""
        return {
            'timestamp': generate_timestamp(),
            'frontend': {
                'count': len(frontend_tests),
                'files': frontend_tests
            },
            'backend': {
                'count': len(backend_tests),
                'files': backend_tests
            },
            'contract': {
                'count': len(contract_tests),
                'files': contract_tests
            },
            'total': len(frontend_tests) + len(backend_tests) + len(contract_tests)
        }


def generate_integration_tests(api_endpoints: List[Dict[str, Any]], 
                               output_dir: str = './tests') -> Dict[str, Any]:
    """生成集成测试的入口函数"""
    generator = IntegrationTestGenerator(output_dir)
    
    frontend_tests = generator.generate_frontend_integration_tests(api_endpoints)
    backend_tests = generator.generate_backend_integration_tests(api_endpoints)
    contract_tests = generator.generate_api_contract_tests(api_endpoints)
    
    summary = generator.generate_test_summary(frontend_tests, backend_tests, contract_tests)
    
    return {
        'frontend_tests': frontend_tests,
        'backend_tests': backend_tests,
        'contract_tests': contract_tests,
        'summary': summary
    }
