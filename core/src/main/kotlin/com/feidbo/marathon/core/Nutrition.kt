package com.feidbo.marathon.core

import java.time.DayOfWeek
import kotlin.math.roundToInt

/**
 * Meal plan for losing fat slowly while training (evidence in docs/evidence/nutrition.md).
 * Everything here is an estimate, not a dietitian's plan: energy needs come from an equation plus an
 * assumed activity factor, and are meant to be corrected from the weekly weigh-in.
 */
data class Food(val id: String, val fdcId: Int, val usdaName: String, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

data class Macros(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)
    companion object { val ZERO = Macros(0.0, 0.0, 0.0, 0.0) }
}

/** [label] is what to buy or scoop; [hint] a household measure from USDA portion data where there is one. */
data class Ingredient(val foodId: String, val grams: Int, val label: String, val hint: String = "") {
    val food: Food get() = usdaFoods.getValue(foodId)
    val macros: Macros get() = Macros(food.kcal * grams / 100, food.protein * grams / 100, food.carbs * grams / 100, food.fat * grams / 100)
}

enum class MealSlot(val title: String, val emoji: String) {
    BREAKFAST("Breakfast", "🥣"), LUNCH("Lunch", "🍱"), DINNER("Dinner", "🍽️"), SNACK("Snack", "🥤")
}

data class Meal(
    val id: String,
    val slot: MealSlot,
    val name: String,
    val items: List<Ingredient>,
    /** How to make it ahead. */
    val prep: String,
    /** What to order or grab when travelling. */
    val away: String,
) {
    val macros: Macros get() = items.fold(Macros.ZERO) { acc, i -> acc + i.macros }
}

data class NutritionProfile(
    val enabled: Boolean = false,
    val weightLb: Double = 184.0,
    val heightIn: Double = 72.0,
    val age: Int = 33,
    val male: Boolean = true,
    val bodyFatPct: Double = 21.0,
    /** Daily living outside training, as a multiple of resting energy. An assumption (no source). */
    val activityFactor: Double = 1.4,
    /** Energy deficit on cutting days, percent of maintenance. */
    val deficitPct: Int = 18,
) {
    val weightKg: Double get() = weightLb / 2.2046
    val fatFreeKg: Double get() = weightKg * (1 - bodyFatPct / 100)
}

data class WeighIn(val dayIndex: Int, val lb: Double)

data class DayTarget(
    val maintenanceKcal: Int,
    val kcal: Int,
    val deficit: Boolean,
    val proteinG: Int,
    /** Extra carbohydrate servings (about 250 kcal each) on top of the four base meals. */
    val fuelServings: Int,
    /** (Energy eaten − running energy) ÷ fat-free mass, kcal per kg. Below 30 is the research warning line. */
    val energyAvailability: Double,
    val note: String,
)

data class ShopItem(val foodId: String, val label: String, val grams: Int)

object Nutrition {
    /** Net cost of running above rest: about 0.87 kcal per kg per km (Brueckner 1991, at ~5 kcal per litre O2). */
    const val RUN_KCAL_PER_KG_KM = 0.87
    const val KM_PER_MILE = 1.609
    /** Lab threshold below which hormones were disrupted (Cupka 2023, citing Loucks). */
    const val LOW_ENERGY_AVAILABILITY = 30.0
    /** Protein while losing weight: 1.6–2.4 g/kg (Hector 2018); the plan uses the middle. */
    const val PROTEIN_G_PER_KG = 2.0
    /** Slow loss kept muscle better than fast: 0.7% vs 1.4% of body weight a week (Garthe 2011). */
    const val MAX_LOSS_PCT_PER_WEEK = 1.0
    const val FUEL_KCAL = 250

    /** Mifflin-St Jeor resting energy, the most reliable of the common equations (Frankenfield 2005). */
    fun restingKcal(p: NutritionProfile): Double =
        10 * p.weightKg + 6.25 * p.heightIn * 2.54 - 5 * p.age + if (p.male) 5 else -161

    fun runKcal(p: NutritionProfile, miles: Double): Double = RUN_KCAL_PER_KG_KM * p.weightKg * KM_PER_MILE * miles

    fun maintenanceKcal(p: NutritionProfile, miles: Double): Double = restingKcal(p) * p.activityFactor + runKcal(p, miles)

    /** No deficit in the taper, or on the half-marathon and race days. */
    fun cuttingDay(day: PlannedDay): Boolean =
        day.phase != Phase.TAPER && day.run != RunKind.HALF_RACE && day.run != RunKind.RACE

    private fun ing(id: String, grams: Int, label: String, hint: String = "") = Ingredient(id, grams, label, hint)

    val meals: List<Meal> = listOf(
        Meal("b_oats", MealSlot.BREAKFAST, "Overnight oats", listOf(
            ing("oats", 60, "Rolled oats (dry)", "about ¾ cup"), ing("yogurt", 200, "Plain nonfat Greek yogurt"),
            ing("milk", 200, "1% milk", "a little under 1 cup"), ing("blueberries", 100, "Blueberries"),
            ing("peanut_butter", 16, "Peanut butter", "1 tbsp"),
        ), "Stir everything into a jar the night before. Make up to 4 jars at once; they keep 3–4 days in the fridge.",
            "Hotel: oatmeal plus Greek yogurt and fruit from the breakfast bar."),
        Meal("b_eggs", MealSlot.BREAKFAST, "Egg & potato box", listOf(
            ing("egg", 150, "Eggs, hard-boiled", "3 large"), ing("potato", 200, "Potato, baked", "1 medium-to-large"),
            ing("spinach", 60, "Spinach"), ing("yogurt", 150, "Plain nonfat Greek yogurt"),
        ), "Boil a batch of eggs and bake potatoes on prep day; box them with spinach. Eat cold or reheat the potato.",
            "Hotel: eggs, potatoes or toast, and a yogurt."),
        Meal("l_chicken", MealSlot.LUNCH, "Chicken & rice bowl", listOf(
            ing("chicken", 170, "Chicken breast (cooked weight)", "6 oz cooked"), ing("rice", 160, "White rice (cooked weight)", "about 1 cup"),
            ing("broccoli", 150, "Broccoli"), ing("olive_oil", 10, "Olive oil", "2 tsp"),
        ), "Grill or bake the chicken, cook the rice, steam the broccoli; portion into 4 boxes. Keeps 3–4 days in the fridge.",
            "Restaurant: grilled chicken with rice and vegetables, sauce on the side."),
        Meal("l_chili", MealSlot.LUNCH, "Turkey & black-bean chili", listOf(
            ing("turkey", 150, "Ground turkey, 93% lean (cooked weight)"), ing("black_beans", 130, "Black beans, canned", "about ½ can"),
            ing("tomatoes", 150, "Canned tomatoes"), ing("rice", 100, "White rice (cooked weight)", "about ⅔ cup"),
        ), "Brown the turkey, add beans, tomatoes and chili spices, simmer 20 min. One pot makes 3–4 portions; freezes well.",
            "Restaurant: chili or a burrito bowl with turkey or chicken, beans and rice; skip sour cream and cheese."),
        Meal("d_salmon", MealSlot.DINNER, "Salmon tray bake", listOf(
            ing("salmon", 150, "Salmon fillet (cooked weight)", "about 5 oz cooked"), ing("sweet_potato", 220, "Sweet potato", "1 large"),
            ing("broccoli", 150, "Broccoli"), ing("olive_oil", 5, "Olive oil", "1 tsp"),
        ), "Roast everything on one tray (about 25 min at 400°F). Cook 2–3 portions at once; best within 3 days.",
            "Restaurant: grilled fish with a potato and vegetables."),
        Meal("d_pasta", MealSlot.DINNER, "Beef pasta", listOf(
            ing("beef", 150, "Ground beef, 93% lean (cooked weight)"), ing("pasta", 160, "Pasta (cooked weight)", "about 1 cup"),
            ing("marinara", 125, "Marinara sauce", "about ½ cup"), ing("spinach", 60, "Spinach"),
        ), "Brown the beef, stir in the sauce and spinach, mix with cooked pasta. Makes 3–4 portions; freezes well.",
            "Restaurant: pasta with meat sauce (ask for a normal, not large, portion) and a side salad."),
        Meal("s_yogurt", MealSlot.SNACK, "Yogurt, banana & almonds", listOf(
            ing("yogurt", 250, "Plain nonfat Greek yogurt"), ing("banana", 118, "Banana", "1 medium"), ing("almonds", 10, "Almonds"),
        ), "Nothing to cook: portion the yogurt into tubs for the week.",
            "Any shop: a Greek yogurt and a banana."),
        Meal("s_tuna", MealSlot.SNACK, "Tuna sandwich & apple", listOf(
            ing("tuna", 120, "Tuna in water, drained", "most of a can"), ing("bread", 64, "Whole-wheat bread", "2 slices"), ing("apple", 182, "Apple", "1 medium"),
        ), "Make the sandwich the night before, or carry the can and bread separately.",
            "Any shop: a tuna or turkey sandwich and a piece of fruit."),
    )

    /** One "fuel serving" is any one of these: about 250 kcal, almost all carbohydrate. */
    val fuelOptions: List<Meal> = listOf(
        Meal("f_bagel", MealSlot.SNACK, "1 plain bagel", listOf(ing("bagel", 99, "Plain bagels", "1 bagel")), "", ""),
        Meal("f_rice", MealSlot.SNACK, "1¼ cups cooked rice (add to lunch or dinner)", listOf(ing("rice", 200, "White rice (cooked weight)")), "", ""),
        Meal("f_banana", MealSlot.SNACK, "2 bananas with a little honey", listOf(ing("banana", 236, "Banana", "2 medium"), ing("honey", 14, "Honey", "2 tsp")), "", ""),
        Meal("f_toast", MealSlot.SNACK, "2 slices of toast with honey", listOf(ing("bread", 64, "Whole-wheat bread", "2 slices"), ing("honey", 21, "Honey", "1 tbsp")), "", ""),
    )

    fun mealsFor(slot: MealSlot): List<Meal> = meals.filter { it.slot == slot }

    /**
     * Which option is on the menu each day, set up for two prep sessions a week: Sunday's batch covers
     * Sunday–Wednesday, Wednesday night's covers Thursday–Saturday.
     */
    fun scheduled(slot: MealSlot, dow: DayOfWeek): Meal {
        val first = dow == DayOfWeek.SUNDAY || dow <= DayOfWeek.WEDNESDAY
        return mealsFor(slot)[if (first) 0 else 1]
    }

    /** Average of the eight base-meal combinations' calories: the four base meals come to about this. */
    val baseKcal: Int = MealSlot.entries.sumOf { s -> mealsFor(s).map { it.macros.kcal }.average() }.roundToInt()

    fun target(p: NutritionProfile, day: PlannedDay): DayTarget {
        val miles = if (day.run.isRun) day.miles else 0.0
        val maintenance = maintenanceKcal(p, miles)
        val deficit = cuttingDay(day)
        val kcal = if (deficit) maintenance * (1 - p.deficitPct / 100.0) else maintenance
        val fuel = ((kcal - baseKcal) / FUEL_KCAL).roundToInt().coerceAtLeast(0)
        val ea = (kcal - runKcal(p, miles)) / p.fatFreeKg
        val note = when {
            !deficit && day.phase == Phase.TAPER -> "Taper: eat at maintenance so you reach the start line fuelled."
            !deficit -> "Race day: no deficit. Fuel the race."
            ea < LOW_ENERGY_AVAILABILITY -> "Today's target is under the low-energy warning line (30 kcal per kg of lean mass). " +
                "If you feel flat, cold, or your running suffers, add a fuel serving."
            else -> "Cutting day: about ${p.deficitPct}% under maintenance."
        }
        return DayTarget(maintenance.roundToInt(), kcal.roundToInt(), deficit, proteinTarget(p), fuel, ea, note)
    }

    fun proteinTarget(p: NutritionProfile): Int = ((PROTEIN_G_PER_KG * p.weightKg) / 5).roundToInt() * 5

    /** Everything for one plan week's base meals, plus that week's fuel servings as bagels, bananas, rice, bread and honey. */
    fun shoppingList(p: NutritionProfile, weekDays: List<PlannedDay>): List<ShopItem> {
        val grams = linkedMapOf<String, Int>()
        val labels = mutableMapOf<String, String>()
        fun add(i: Ingredient, times: Int = 1) {
            grams[i.foodId] = (grams[i.foodId] ?: 0) + i.grams * times
            labels.putIfAbsent(i.foodId, i.label)
        }
        var fuel = 0
        for (d in weekDays) {
            MealSlot.entries.forEach { s -> scheduled(s, d.date.dayOfWeek).items.forEach { add(it) } }
            fuel += target(p, d).fuelServings
        }
        // Spread the fuel servings evenly over the four options.
        fuelOptions.forEachIndexed { i, m -> m.items.forEach { add(it, (fuel + fuelOptions.size - 1 - i) / fuelOptions.size) } }
        return grams.map { (id, g) -> ShopItem(id, labels.getValue(id), g) }
    }

    /** Percent of body weight lost per week between the latest weigh-in and the last one at least 6 days before it. */
    fun weeklyLossPct(weighIns: List<WeighIn>): Double? {
        val sorted = weighIns.sortedBy { it.dayIndex }
        val last = sorted.lastOrNull() ?: return null
        val prev = sorted.lastOrNull { last.dayIndex - it.dayIndex >= 6 } ?: return null
        val weeks = (last.dayIndex - prev.dayIndex) / 7.0
        return (prev.lb - last.lb) / prev.lb * 100 / weeks
    }

    fun weighInAdvice(weighIns: List<WeighIn>): String? {
        val rate = weeklyLossPct(weighIns) ?: return null
        val r = fmt("%.1f", kotlin.math.abs(rate))
        return when {
            rate > MAX_LOSS_PCT_PER_WEEK -> "You're losing about $r% of your weight a week — faster than the 0.7% that kept muscle in " +
                "athletes (Garthe 2011). Add a fuel serving every day."
            rate < 0.0 -> "Up about $r% a week. One weigh-in is noisy (water, salt, a long run); if it holds for two weeks, the " +
                "activity estimate is too high — lower it in the Food settings."
            rate < 0.2 -> "About level ($r% a week). If it stays level for two weeks, lower the activity estimate in the Food settings."
            else -> "Losing about $r% a week — on track."
        }
    }
}

/** "1.2 kg / 2 lb 10 oz" style amounts for the shopping list. */
fun shopAmount(grams: Int): String {
    val lb = grams / 453.59
    val metric = if (grams >= 1000) fmt("%.1f kg", grams / 1000.0) else "$grams g"
    val us = if (lb >= 1) fmt("%.1f lb", lb) else fmt("%.0f oz", grams / 28.35)
    return "$metric · $us"
}
