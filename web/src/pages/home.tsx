export function HomePage() {
  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-bold">SkillHub</h1>
        <p className="text-xl text-muted-foreground">技能注册中心</p>
      </div>
      <div className="space-y-4">
        <p className="text-muted-foreground">
          欢迎来到 SkillHub，这是一个技能管理和分享平台。
        </p>
        <p className="text-muted-foreground">
          请登录以访问完整功能。
        </p>
      </div>
    </div>
  )
}
