package com.dgp.admin;

import com.dgp.auth.MenuCatalog;
import com.dgp.common.Db;
import com.dgp.common.M;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 启动时把 MenuCatalog 中新增的菜单补录到 sys_menu（不覆盖已维护的菜单），
 * 保证升级后新增功能能在菜单管理中看到并授权。
 */
@Component
public class MenuBootstrap implements ApplicationRunner {

    private final Db db;

    public MenuBootstrap(Db db) {
        this.db = db;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int i = 0;
            for (M m : MenuCatalog.all()) {
                i += 10;
                String code = String.valueOf(m.get("code"));
                if (db.one("SELECT id FROM sys_menu WHERE code=?", code) != null) continue;
                String domain = String.valueOf(m.get("domain"));
                db.insert("INSERT INTO sys_menu(tenant_id,code,title,path,icon,perm,parent_code,sort_no,status,visible,"
                                + "create_time,update_time) VALUES(1,?,?,?,?,?,?,?,'ENABLED',1,?,?)",
                        code, m.get("name"), m.get("path"), m.get("icon"), m.get("perm"),
                        "portal".equals(domain) ? null : domain, i, LocalDateTime.now(), LocalDateTime.now());
            }
        } catch (Exception ignore) {
            // 表未就绪时静默，下一次启动再补
        }
    }
}
