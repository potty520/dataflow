package com.zhangye.dataflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhangye.dataflow.entity.AssetField;
import com.zhangye.dataflow.entity.GovModelTable;
import com.zhangye.dataflow.mapper.AssetFieldMapper;
import com.zhangye.dataflow.mapper.AssetTableMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ModelComparer {

    private final AssetTableMapper assetTableMapper;
    private final AssetFieldMapper assetFieldMapper;

    public ModelComparer(AssetTableMapper assetTableMapper, AssetFieldMapper assetFieldMapper) {
        this.assetTableMapper = assetTableMapper;
        this.assetFieldMapper = assetFieldMapper;
    }

    public Map<String, Object> compare(GovModelTable model, List<AssetField> modelFields) {
        Map<String, Object> diff = new LinkedHashMap<>();
        List<Map<String, String>> designOnly = new ArrayList<>();
        List<Map<String, String>> actualOnly = new ArrayList<>();
        List<Map<String, String>> typeDiff = new ArrayList<>();
        List<Map<String, String>> commentDiff = new ArrayList<>();

        // Get actual fields from asset_field for the model's associated table
        LambdaQueryWrapper<AssetField> qw = new LambdaQueryWrapper<>();
        qw.eq(AssetField::getTableId, model.getTableEn()); // approximation - in real impl use asset_table lookup
        List<AssetField> actualFields = assetFieldMapper.selectList(qw);

        Map<String, AssetField> modelFieldMap = modelFields.stream()
                .collect(Collectors.toMap(f -> f.getFieldEn().toLowerCase(), f -> f, (a, b) -> a));
        Map<String, AssetField> actualFieldMap = actualFields.stream()
                .collect(Collectors.toMap(f -> f.getFieldEn().toLowerCase(), f -> f, (a, b) -> a));

        // Fields in design but not in actual
        for (String fieldEn : modelFieldMap.keySet()) {
            if (!actualFieldMap.containsKey(fieldEn)) {
                AssetField f = modelFieldMap.get(fieldEn);
                Map<String, String> m = new HashMap<>();
                m.put("field", f.getFieldEn());
                m.put("fieldCn", f.getFieldCn());
                m.put("type", f.getFieldType());
                designOnly.add(m);
            }
        }

        // Fields in actual but not in design
        for (String fieldEn : actualFieldMap.keySet()) {
            if (!modelFieldMap.containsKey(fieldEn)) {
                AssetField f = actualFieldMap.get(fieldEn);
                Map<String, String> m = new HashMap<>();
                m.put("field", f.getFieldEn());
                m.put("fieldCn", f.getFieldCn());
                m.put("type", f.getFieldType());
                actualOnly.add(m);
            }
        }

        // Type and comment differences
        for (String fieldEn : modelFieldMap.keySet()) {
            if (actualFieldMap.containsKey(fieldEn)) {
                AssetField mf = modelFieldMap.get(fieldEn);
                AssetField af = actualFieldMap.get(fieldEn);

                if (mf.getFieldType() != null && !mf.getFieldType().equalsIgnoreCase(af.getFieldType())) {
                    Map<String, String> m = new HashMap<>();
                    m.put("field", mf.getFieldEn());
                    m.put("designed", mf.getFieldType());
                    m.put("actual", af.getFieldType());
                    typeDiff.add(m);
                }

                String mc = mf.getDescription() != null ? mf.getDescription() : "";
                String ac = af.getDescription() != null ? af.getDescription() : "";
                if (!mc.equals(ac)) {
                    Map<String, String> m = new HashMap<>();
                    m.put("field", mf.getFieldEn());
                    m.put("designed", mc);
                    m.put("actual", ac);
                    commentDiff.add(m);
                }
            }
        }

        // Generate ALTER TABLE statements
        List<String> alterStatements = new ArrayList<>();
        Map<String, String> typeMapping = new LinkedHashMap<>();
        typeMapping.put("STRING", "VARCHAR(255)");
        typeMapping.put("INT", "INT");
        typeMapping.put("BIGINT", "BIGINT");
        typeMapping.put("DOUBLE", "DOUBLE");
        typeMapping.put("DECIMAL", "DECIMAL(18,6)");
        typeMapping.put("DATE", "DATE");
        typeMapping.put("TIMESTAMP", "DATETIME");
        typeMapping.put("BOOLEAN", "TINYINT(1)");

        for (Map<String, String> f : designOnly) {
            String mysqlType = typeMapping.getOrDefault(f.get("type"), "VARCHAR(255)");
            alterStatements.add("ALTER TABLE " + model.getTableEn() + " ADD COLUMN " + f.get("field") + " " + mysqlType + " COMMENT '" + nvl(f.get("fieldCn")) + "';");
        }
        for (Map<String, String> f : typeDiff) {
            String mysqlType = typeMapping.getOrDefault(f.get("designed"), "VARCHAR(255)");
            alterStatements.add("ALTER TABLE " + model.getTableEn() + " MODIFY COLUMN " + f.get("field") + " " + mysqlType + ";");
        }

        diff.put("designOnly", designOnly);
        diff.put("actualOnly", actualOnly);
        diff.put("typeDiff", typeDiff);
        diff.put("commentDiff", commentDiff);
        diff.put("alterStatements", alterStatements);
        diff.put("isConsistent", designOnly.isEmpty() && actualOnly.isEmpty() && typeDiff.isEmpty());

        return diff;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
