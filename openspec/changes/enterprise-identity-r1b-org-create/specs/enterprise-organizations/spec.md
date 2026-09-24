## ADDED Requirements

### Requirement: Platform administrator creates an organization with an initial owner

系统 SHALL 只允许 `SUPER_ADMIN` 使用唯一 slug、显示名和一个已有且可用的平台账号创建 Organization。创建 SHALL 在单个事务中生成 ACTIVE Organization、该账号的 ACTIVE Membership、`ORG_OWNER` role binding 和组织审计记录。创建者不得因其平台角色自动成为组织成员。

#### Scenario: Successful creation

- **WHEN** 平台管理员指定有效 slug、显示名和 ACTIVE 的初始 owner
- **THEN** 系统创建以上四类记录，返回组织 ID 与状态，并使初始 owner 可以访问组织详情

#### Scenario: Invalid owner or duplicate slug

- **WHEN** 初始 owner 不存在、禁用、为系统账号、已合并，或 slug 已被占用
- **THEN** 系统返回对应的 404 或 409，且不留下部分 Organization、Membership、Role Binding 或成功审计

#### Scenario: Platform role is not tenant membership

- **WHEN** 创建者仅持有 `SUPER_ADMIN` 平台角色而不是新组织成员
- **THEN** 其访问新组织的租户详情接口被拒绝
