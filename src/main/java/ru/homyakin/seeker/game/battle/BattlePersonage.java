package ru.homyakin.seeker.game.battle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.homyakin.seeker.game.personage.models.Characteristics;
import ru.homyakin.seeker.utils.RandomUtils;

public class BattlePersonage implements Cloneable {
    private static final Logger logger = LoggerFactory.getLogger(BattlePersonage.class);
    private final long id;
    private int health;
    private long damageDealt = 0L;
    private long damageBlocked = 0L;
    private final BattleCharacteristics characteristics;

    public BattlePersonage(
        long id,
        Characteristics characteristics
    ) {
        this.id = id;
        this.health = characteristics.health();
        this.characteristics = BattleCharacteristics.from(characteristics);
    }

    public long id() {
        return id;
    }

    public long damageDealtAndTaken() {
        return damageDealt + damageBlocked;
    }

    public int health() {
        return health;
    }

    public boolean isDead() {
        return health <= 0;
    }

    public void dealDamageToPersonage(BattlePersonage enemy) {
        double attack = this.characteristics.attack + this.characteristics.strength * strengthMultiplier
            - enemy.characteristics.defense * defenseMultiplier;
        attack = Math.max(minAttack(), attack);
        attack *= critBonus(enemy.characteristics.agility);
        damageDealt += enemy.takeDamageAndReturnDealtDamage((int) attack, this);
    }

    private int takeDamageAndReturnDealtDamage(int attack, BattlePersonage enemy) {
        damageBlocked += attack;
        if (isDodge()) {
            logger.debug("Personage {} missed {}", enemy.id, id);
            return 0;
        }
        final int dealtDamage;
        if (health < attack) {
            dealtDamage = health;
            health = 0;
        } else {
            dealtDamage = attack;
            health -= attack;
        }
        logger.debug("Personage {} attacked {} by {} damage", enemy.id, id, dealtDamage);
        return dealtDamage;
    }

    private boolean isDodge() {
        var dodgeChance = baseDodgeChance + this.characteristics.agility * agilityDodgeChanceMultiplier;
        dodgeChance = Math.min(maxDodgeChance, dodgeChance);
        return RandomUtils.getInInterval(1, 100) <= dodgeChance;
    }

    private double critBonus(int enemyAgility) {
        final var wisdom = this.characteristics.wisdom;
        var critChance = baseCritChance + wisdom * wisdomCritChanceMultiplier;
        critChance = Math.min(maxCritChance, critChance);
        if (RandomUtils.getInInterval(1, 100) <= critChance) {
            return baseCritMulti + (Math.max(wisdom - enemyAgility * agilityCritMultiMultiplier, 0)) * wisdomCritMultiplier;
        } else {
            return baseCritMulti;
        }
    }

    private double minAttack() {
        return characteristics.attack() * minAttackPercent;
    }

    // TODO вынести в базу
    private static final int maxDodgeChance = 90;
    private static final int baseDodgeChance = 10;
    private static final double baseCritMulti = 2;
    private static final int baseCritChance = 10;
    private static final int maxCritChance = 90;
    private static final double minAttackPercent = 0.1;
    private static final double strengthMultiplier = 1.1;
    private static final double defenseMultiplier = 0.7;
    private static final double agilityDodgeChanceMultiplier = 1.6;
    private static final double agilityCritMultiMultiplier = 0.4;
    private static final double wisdomCritMultiplier = 0.04;
    private static final double wisdomCritChanceMultiplier = 2;

    record BattleCharacteristics(
        int attack,
        int defense,
        int strength,
        int agility,
        int wisdom
    ) implements Cloneable {
        public static BattleCharacteristics from(Characteristics characteristics) {
            return new BattleCharacteristics(
                characteristics.attack(),
                characteristics.defense(),
                characteristics.strength(),
                characteristics.agility(),
                characteristics.wisdom()
            );
        }

        @Override
        public BattleCharacteristics clone() {
            try {
                return (BattleCharacteristics) super.clone();
            } catch (CloneNotSupportedException e) {
                //Не может быть в record
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    @SuppressWarnings("super")
    public BattlePersonage clone() {
        return new BattlePersonage(
            id,
            health,
            characteristics.clone()
        );
    }

    private BattlePersonage(
        long id,
        int health,
        BattleCharacteristics battleCharacteristics
    ) {
        this.id = id;
        this.health = health;
        this.characteristics = battleCharacteristics;
    }
}
