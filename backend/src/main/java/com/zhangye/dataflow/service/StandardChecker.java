package com.zhangye.dataflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.entity.AssetField;
import com.zhangye.dataflow.entity.GovCodeSet;
import com.zhangye.dataflow.entity.GovStandardCatalog;
import com.zhangye.dataflow.mapper.AssetFieldMapper;
import com.zhangye.dataflow.mapper.GovCodeSetMapper;
import com.zhangye.dataflow.mapper.GovStandardCatalogMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StandardChecker {

    private final GovCodeSetMapper codeSetMapper;
    private final GovStandardCatalogMapper catalogMapper;
    private final AssetFieldMapper assetFieldMapper;

    public StandardChecker(GovCodeSetMapper codeSetMapper, GovStandardCatalogMapper catalogMapper,
                           AssetFieldMapper assetFieldMapper) {
        this.codeSetMapper = codeSetMapper;
        this.catalogMapper = catalogMapper;
        this.assetFieldMapper = assetFieldMapper;
    }

    public Map<String, Object> checkDropStandard(Long stdCatalogId, Long modelTableId, Long tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Get catalog info
        GovStandardCatalog catalog = catalogMapper.selectById(stdCatalogId);
        if (catalog == null) {
            result.put("error", "标准目录不存在");
            return result;
        }

        // Get standard code sets under this catalog
        List<GovCodeSet> codeSets = codeSetMapper.selectList(
                new LambdaQueryWrapper<GovCodeSet>()
                        .eq(GovCodeSet::getTenantId, tenantId)
                        .eq(GovCodeSet::getCatalogId, stdCatalogId));

        // Get fields for the model table
        LambdaQueryWrapper<AssetField> qw = new LambdaQueryWrapper<>();
        if (modelTableId != null) {
            qw.eq(AssetField::getTableId, modelTableId);
        }
        List<AssetField> fields = assetFieldMapper.selectList(qw);

        Map<String, GovCodeSet> codeMap = codeSets.stream()
                .collect(Collectors.toMap(c -> c.getCode().toLowerCase(), c -> c, (a, b) -> a));

        List<Map<String, String>> matchedFields = new ArrayList<>();
        List<Map<String, String>> unmatchedFields = new ArrayList<>();
        List<Map<String, String>> deviationReport = new ArrayList<>();

        for (AssetField field : fields) {
            String fieldEn = field.getFieldEn().toLowerCase();
            if (codeMap.containsKey(fieldEn)) {
                GovCodeSet cs = codeMap.get(fieldEn);
                Map<String, String> m = new HashMap<>();
                m.put("field", field.getFieldEn());
                m.put("fieldCn", field.getFieldCn());
                m.put("codeSetCode", cs.getCode());
                m.put("codeSetName", cs.getName());
                m.put("codeSetValue", cs.getCodeValue());
                matchedFields.add(m);
            } else {
                Map<String, String> m = new HashMap<>();
                m.put("field", field.getFieldEn());
                m.put("fieldCn", field.getFieldCn());
                m.put("reason", "未找到匹配的标准代码集");
                unmatchedFields.add(m);
            }
        }

        // Check if code sets exist but no field maps to them
        for (GovCodeSet cs : codeSets) {
            boolean hasMapping = fields.stream()
                    .anyMatch(f -> f.getFieldEn().equalsIgnoreCase(cs.getCode()));
            if (!hasMapping) {
                Map<String, String> m = new HashMap<>();
                m.put("codeSet", cs.getCode());
                m.put("codeSetName", cs.getName());
                m.put("reason", "标准代码集未映射到任何字段");
                deviationReport.add(m);
            }
        }

        result.put("catalogName", catalog.getName());
        result.put("catalogType", catalog.getCatalogType());
        result.put("totalFields", fields.size());
        result.put("matchedCount", matchedFields.size());
        result.put("unmatchedCount", unmatchedFields.size());
        result.put("deviationCount", deviationReport.size());
        result.put("matchedFields", matchedFields);
        result.put("unmatchedFields", unmatchedFields);
        result.put("deviationReport", deviationReport);
        result.put("complianceRate", fields.isEmpty() ? 0 :
                String.format("%.1f%%", 100.0 * matchedFields.size() / fields.size()));

        return result;
    }
}
