## Implementation

- [x] 新增平台级 Organization 创建 API，验证初始 owner，原子写入组织、成员、角色和审计。
- [x] 保持平台角色与租户成员权限分离，不开放企业登录与组织同步。
- [x] 从当前分支应用运行结果生成并提交 OpenAPI 类型。

## Verification

- [x] 集成测试覆盖成功、仅 owner 可进入组织详情、非管理员、禁用/不存在 owner 和重复 slug。
- [x] 完整后端测试、Web typecheck/lint 和 OpenSpec strict validation 通过。
- [ ] 检查最终 diff、敏感信息、精确 SHA 与预览环境；经人工评审后再提 PR/合并。
