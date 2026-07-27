物品数据示例：
magic_sword:
  material: DIAMOND_SWORD
  customName: "<light_purple>魔剑"
  lore:
    - <yellow>一把神秘的魔剑
  
magic_shield:
  material: SHIELD
  customName: "<gold>神圣护盾"
  lore:
    - <yellow>坚不可摧的盾牌
  unbreakable: true
组件解析示例；
example_item:
  material: DIAMOND_SWORD # 物品种类，必填
  customName: "炒鸡剑" # 自定义名称，默认无斜体，支持 MiniMessage
  itemModel: IRON_SWORD # 物品模型
  amount: 1 # 物品数量，不建议设置
  lore: # 物品提示框中的描述信息，默认无斜体，支持 MiniMessage
    - "<gray>第一行描述"
    - ""
    - "<yellow>第三行描述"
  glint: true # 覆盖设置附魔光效
  enchantments: # 魔咒，如果物品是附魔书，自动改为写入所存储的“无活性”魔咒
    - minecraft:sharpness: 5
    - minecraft:fire_aspect: 2
    - mczju:custom_enchantment: 1 # 可以是数据包自定义的魔咒
  maxStackSize: 1 # 最大堆叠数
  maxDurability: 100 # 最大耐久度
  remainingDurability: 100 # 剩余耐久度
  unbreakable: true # 无法破坏
  repairCost: 999 # 设置在铁砧上的累计惩罚值
  food: # 定义食物属性。注意，如果希望一个本身不是食物的物品可以食用，还需要定义 consumable
    canAlwaysEat: true # 饥饿值已满时能否食用，默认否
    nutrition: 8 # 恢复的饥饿值（整数，每半个鸡腿为 1）
    saturation: 8.0 # 恢复的饱和度（浮点数）
  consumable: # 定义一个物品为长按后可消耗（通常是食物）
    animation: BLOCK # 食用期间的动作，默认 EAT
    consumeSeconds: 1.6 # 食用所需秒数，默认 1.6
    hasConsumeParticles: true # 食用时是否产生粒子，默认 true
    applyEffects: # 食用后添加的状态效果
      - id: "minecraft:strength"
        amplifier: 1 # 倍率，默认为 0，为 n 代表该状态为 n + 1 级
        durationTicks: 100 # 持续时间（tick）
        ambient: true # 是否为信标施加（也就是粒子比较淡），默认 false
        showIcon: true # 是否显示图标，默认与 showParticles 一致
        showParticles: true # 是否显示粒子，默认为 true
    applyEffectsProbability: 0.8 # 施加这些状态效果的概率
    clearAllEffects: true # 为 true 时，食用后清除所有状态效果（就像牛奶）
    onConsumeSound: "entity.player.small_fall" # 食用期间的音效
    consumedSound: "item.honey_bottle.drink" # 食用完毕后的音效
  dyedColor: "#112233" # 适用于皮革盔甲，所染颜色，必须加引号
  attributeModifiers: # 属性修饰符
    - id: minecraft:base_attack_damage
      attribute: minecraft:attack_damage
      amount: 10
      operation: ADD_NUMBER
      slot: mainhand
    - id: minecraft:base_attack_speed
      attribute: attack_speed
      amount: 2
      operation: ADD_NUMBER
      slot: mainhand
  shieldData:
    baseColor: YELLOW
    patterns:
      - type: minecraft:stripe_downright
        color: ORANGE
      - type: minecraft:border
        color: ORANGE
  headProfile: # 头颅 Profile，仅对 PLAYER_HEAD 生效；只支持已明确 textures 的自定义头，不会联网获取玩家皮肤
    name: "wither" # 可选，任意非空字符串
    id: # 可选，Profile UUID，对应 minecraft:profile 的 id:[I;...]；不填则自动生成，因此建议添加以保证任何时候生成的物品均完全相同
      - -1318804973
      - -2013509262
      - -1657906093
      - -540136017
    # textures 必填，头颅纹理 base64
    textures: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTE2OWM5MGM4ODc0YWI1NzViMjAxYjYxNmE2OWVhYzdlMGI1YWM2OWJiY2NjYmIyNzcyZTM2Nzc2ZmU2OTQ0MSJ9fX0="
  tooltipDisplay:
    hideTooltip: false # 完全隐藏提示框，即鼠标悬停时什么都不显示
    hiddenComponents: # 隐藏部分提示框文本（暂时只支持常见的部分几种）
      - dyed_color
  pdc:
    - key: my_plugin:item_id # 必须带冒号，不支持从插件名生成。会自动将大写字母改为小写
      type: STRING # 只支持 STRING, INTEGER, LONG, DOUBLE, FLOAT, BOOLEAN
      value: "special_sword"
  useCooldown: # ItemCreator 原生物品栏冷却显示（秒数宜与玩法 CD 一致）
    cooldownGroup: "maggoteers:example_weapon"
    seconds: 10
    - key: mczju:item_cd
      type: LONG
      value: "8000"