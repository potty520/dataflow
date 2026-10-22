package com.dgp.auth;

import com.dgp.common.M;

import java.util.ArrayList;
import java.util.List;

/** 菜单目录（后端驱动前端动态路由），权限编码与 00 基线 §6.4 一致。 */
public final class MenuCatalog {

    private MenuCatalog() {
    }

    private static M item(String code, String name, String path, String icon, String perm, String domain) {
        return M.of("code", code, "name", name, "path", path, "icon", icon, "perm", perm, "domain", domain);
    }

    public static List<M> all() {
        List<M> out = new ArrayList<>();
        out.add(item("overview", "治理概览", "/overview", "DataBoard", "metadata:overview:view", "portal"));
        out.add(item("metadata", "元数据管理", "/metadata", "Coin", "metadata:table:view", "metadata"));
        out.add(item("metadata-ds", "数据源管理", "/metadata/datasources", "Link", "metadata:datasource:view", "metadata"));
        out.add(item("metadata-collect", "采集任务", "/metadata/collect", "Refresh", "metadata:collect:view", "metadata"));
        out.add(item("lineage", "血缘分析", "/lineage", "Share", "metadata:lineage:view", "metadata"));
        out.add(item("model", "数据建模", "/model", "Grid", "model:model:view", "model"));
        out.add(item("standard", "数据标准", "/standard", "Collection", "standard:element:view", "standard"));
        out.add(item("standard-map", "标准映射与落地", "/standard/mapping", "Connection", "standard:mapping:view", "standard"));
        out.add(item("integration", "数据集成", "/integration", "Operation", "integration:job:view", "integration"));
        out.add(item("workflow", "调度与工作流", "/workflow", "Timer", "workflow:definition:view", "workflow"));
        out.add(item("quality", "数据质量", "/quality", "CircleCheck", "quality:rule:view", "quality"));
        out.add(item("quality-stream", "流式质检", "/quality/stream", "Odometer", "quality:stream:view", "quality"));
        out.add(item("quality-issue", "问题闭环", "/quality/issues", "Warning", "quality:issue:view", "quality"));
        out.add(item("quality-report", "质量报告", "/quality/reports", "Document", "quality:report:view", "quality"));
        out.add(item("security", "数据安全", "/security", "Lock", "security:mask:view", "security"));
        out.add(item("audit", "审计与告警", "/security/audit", "View", "security:audit:view", "security"));
        out.add(item("asset", "数据资产", "/asset", "Files", "asset:catalog:view", "asset"));
        out.add(item("asset-map", "资产地图", "/asset/map", "MapLocation", "asset:map:view", "asset"));
        out.add(item("service", "数据服务", "/service", "Promotion", "service:api:view", "service"));
        out.add(item("migration", "数据迁移", "/migration", "Switch", "migration:project:view", "integration"));
        out.add(item("migration-tools", "远程与文件迁移", "/migration/tools", "Files", "migration:file:view", "integration"));
        out.add(item("industry", "行业适配", "/industry", "Box", "industry:package:view", "industry"));
        out.add(item("admin", "系统管理", "/admin", "Setting", "sys:config:view", "admin"));
        return out;
    }
}
