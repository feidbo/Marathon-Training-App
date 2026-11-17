package com.feidbo.marathon.core

import kotlin.math.roundToInt

/**
 * Meal plan for losing fat slowly while training (evidence in docs/evidence/nutrition.md).
 * Everything here is an estimate, not a dietitian's plan: energy needs come from an equation plus an
 * assumed activity factor, and are meant to be corrected from the weekly weigh-in.
 */
data class Food(val id: String, val fdcId: Int, val usdaName: String, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

data class Macros(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)
    operator fun times(k: Double) = Macros(kcal * k, protein * k, carbs * k, fat * k)
    companion object { val ZERO = Macros(0.0, 0.0, 0.0, 0.0) }
}

/** Grocery departments, in the order most shops are walked. */
enum class Dept(val title: String) {
    PRODUCE("Produce"), MEAT("Meat"), DAIRY("Dairy & eggs"), FROZEN("Frozen"), BAKERY("Bakery"), PANTRY("Pantry & canned")
}

private val deptOf: Map<String, Dept> = mapOf(
    "banana" to Dept.PRODUCE, "apple" to Dept.PRODUCE, "spinach" to Dept.PRODUCE, "potato" to Dept.PRODUCE, "sweet_potato" to Dept.PRODUCE,
    "chicken" to Dept.MEAT, "turkey" to Dept.MEAT, "beef" to Dept.MEAT,
    "yogurt" to Dept.DAIRY, "milk" to Dept.DAIRY, "egg" to Dept.DAIRY, "cottage" to Dept.DAIRY,
    "broccoli" to Dept.FROZEN, "blueberries" to Dept.FROZEN,
    "bread" to Dept.BAKERY, "bagel" to Dept.BAKERY,
    "whey" to Dept.PANTRY, "rice" to Dept.PANTRY, "honey" to Dept.PANTRY,
)

/**
 * A plain food added to a meal (not part of a recipe), with USDA values. [label] is what to buy; [hint] a household
 * measure. For the shopping list, [unitGrams]/[unitName] turn grams into countable units (e.g. 118 g = 1 banana).
 */
data class Ingredient(
    val foodId: String,
    val grams: Int,
    val label: String,
    val hint: String = "",
    val unitGrams: Int? = null,
    val unitName: String = "",
) {
    val food: Food get() = usdaFoods.getValue(foodId)
    val dept: Dept get() = deptOf[foodId] ?: Dept.PANTRY
    val macros: Macros get() = Macros(food.kcal * grams / 100, food.protein * grams / 100, food.carbs * grams / 100, food.fat * grams / 100)
}

/** Three meals a day, no snacks (user request, Nov 2026). Fuel servings are extras, not a slot. */
enum class MealSlot(val title: String, val emoji: String) {
    BREAKFAST("Breakfast", "🥣"), LUNCH("Lunch", "🍱"), DINNER("Dinner", "🍽️")
}

/** One line of a published recipe, plus what it means for the shopping list ([dept] null = not bought, e.g. water). */
data class RecipeIngredient(val line: String, val amount: Double?, val unit: String, val item: String, val dept: Dept?)

/** A recipe exactly as published by Food Hero (Oregon State University Extension), nutrition from its label. */
data class Recipe(
    val id: String,
    val title: String,
    val url: String,
    val makes: String,
    val prep: String,
    val cook: String,
    val servings: Int,
    val servingSize: String,
    val perServing: Macros,
    val ingredients: List<RecipeIngredient>,
    val steps: List<String>,
    val notes: List<String>,
)

data class Meal(
    val id: String,
    /** Null for a fuel serving, which isn't one of the day's meals. */
    val slot: MealSlot?,
    val name: String,
    /** Plain foods eaten with it (or the whole meal, when there's no recipe). */
    val items: List<Ingredient>,
    /** Short how-to-serve line (for a recipe meal: what goes with it). */
    val prep: String,
    /** What to order or grab when travelling. */
    val away: String,
    val recipeId: String? = null,
    /** How many of the recipe's label servings make one meal. */
    val recipeServings: Double = 0.0,
) {
    val recipe: Recipe? get() = recipeId?.let { foodHeroRecipes.getValue(it) }
    val macros: Macros get() = items.fold(recipe?.perServing?.times(recipeServings) ?: Macros.ZERO) { acc, i -> acc + i.macros }
    /** "2 servings (1 cup each)" — the portion of the recipe in one meal. */
    val portion: String? get() = recipe?.let { r -> "${amountText(recipeServings)} × ${r.servingSize}" }
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
    /** Extra carbohydrate servings (about 250 kcal each) on top of the three meals. */
    val fuelServings: Int,
    /** (Energy eaten − running energy) ÷ fat-free mass, kcal per kg. Below 30 is the research warning line. */
    val energyAvailability: Double,
    val note: String,
)

data class ShopItem(val key: String, val label: String, val amount: String, val dept: Dept)

/** One cooking session's worth of a recipe: [batches] times the published recipe. */
data class PrepBatch(val recipe: Recipe, val batches: Double, val forMeal: Meal)

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

    private fun ing(id: String, grams: Int, label: String, hint: String = "", unitGrams: Int? = null, unitName: String = "") =
        Ingredient(id, grams, label, hint, unitGrams, unitName)

    /**
     * Two weekly menus of Food Hero recipes (Oregon State University Extension: recipes tested in a test kitchen
     * and analysed for nutrition), in the styles you picked: Tex-Mex, Asian-style, and simple roasted. Three meals,
     * no snacks, and a light breakfast (user request, Nov 2026); lunch and dinner carry most of the food. Portions
     * are multiples of each recipe's own serving; the only additions are plain foods (Greek yogurt, a scoop of
     * protein powder, plain rice) to reach the protein target.
     */
    val meals: List<Meal> = listOf(
        breakfast("b_quiche_a"),
        breakfast("b_quiche_b"),
        Meal("l_pepper_bake", MealSlot.LUNCH, "Chicken pepper bake", emptyList(),
            "2½ cups.",
            "Restaurant: grilled chicken with roasted potatoes and vegetables.",
            recipeId = "chicken-pepper-bake", recipeServings = 2.5),
        Meal("l_bean_burrito", MealSlot.LUNCH, "Chicken & black bean salsa burritos", emptyList(),
            "1½ burritos (the recipe's 3 half-burrito servings).",
            "Restaurant: a chicken burrito or burrito bowl with black beans; go easy on cheese and sour cream.",
            recipeId = "chicken-and-black-bean-salsa-burritos", recipeServings = 3.0),
        Meal("d_sweet_sour", MealSlot.DINNER, "Sweet and sour chicken over rice", listOf(
            ing("rice", 158, "White rice (cooked)", "1 cup cooked", unitGrams = 158, unitName = "cups cooked"),
        ), "2½ cups over 1 cup of cooked rice (the recipe's note: serve over rice).",
            "Restaurant: a chicken stir-fry with steamed rice.",
            recipeId = "sweet-and-sour-chicken", recipeServings = 2.5),
        Meal("d_cabbage_stir_fry", MealSlot.DINNER, "Chicken cabbage stir-fry over rice", listOf(
            ing("rice", 237, "White rice (cooked)", "1½ cups cooked", unitGrams = 158, unitName = "cups cooked"),
        ), "3½ cups over 1½ cups of cooked rice.",
            "Restaurant: a chicken and vegetable stir-fry with steamed rice.",
            recipeId = "chicken-cabbage-stir-fry", recipeServings = 3.5),
    )

    /** The light breakfast, the same both weeks: 2 quiche muffins and Greek yogurt with a scoop of protein powder. */
    private fun breakfast(id: String) = Meal(id, MealSlot.BREAKFAST, "Veggie quiche muffins + protein yogurt", listOf(
        ing("yogurt", 200, "Plain nonfat Greek yogurt"),
        ing("whey", 30, "Whey protein isolate powder", "1 scoop (30 g); app uses USDA's 17 g protein — your tub's label may say more",
            unitGrams = 30, unitName = "scoops"),
    ), "2 muffins, and a tub of Greek yogurt with the scoop of protein powder stirred in.",
        "Hotel: an omelette or two eggs, plus a Greek yogurt.",
        recipeId = "veggie-quiche-muffins", recipeServings = 2.0)

    /** One "fuel serving" is any one of these: about 250 kcal, almost all carbohydrate. */
    val fuelOptions: List<Meal> = listOf(
        Meal("f_bagel", null, "1 plain bagel", listOf(ing("bagel", 99, "Plain bagels", "1 bagel", unitGrams = 99, unitName = "bagels")), "", ""),
        Meal("f_rice", null, "1¼ cups cooked rice (add to lunch or dinner)", listOf(ing("rice", 198, "White rice (cooked)", unitGrams = 158, unitName = "cups cooked")), "", ""),
        Meal("f_banana", null, "2 bananas with a little honey", listOf(ing("banana", 236, "Banana", "2 medium", unitGrams = 118, unitName = "bananas"), ing("honey", 14, "Honey", "2 tsp", unitGrams = 7, unitName = "tsp")), "", ""),
        Meal("f_toast", null, "2 slices of toast with honey", listOf(ing("bread", 64, "Whole-wheat bread", "2 slices", unitGrams = 32, unitName = "slices"), ing("honey", 21, "Honey", "1 tbsp", unitGrams = 7, unitName = "tsp")), "", ""),
    )

    fun mealsFor(slot: MealSlot): List<Meal> = meals.filter { it.slot == slot }

    /**
     * One prep session a week (Sunday, for the Monday–Sunday plan week that follows): the same three meals
     * all week, and the other set the week after, so there is one batch of each to cook.
     */
    fun scheduled(slot: MealSlot, week: Int): Meal = mealsFor(slot)[Math.floorMod(week, 2)]

    const val PREP_STORAGE = "The recipes say: refrigerate leftovers within 2 hours. They don't say how long food keeps or " +
        "give freezing steps. For a whole week from one cook, a common approach is Monday–Wednesday portions in the fridge " +
        "and Thursday–Sunday in the freezer, thawed in the fridge the night before — that's general food-safety practice, " +
        "not from the recipes or a source checked for this app."

    /** Batches of each recipe to cook on prep day so one menu covers 7 days, rounded up to a half batch. */
    fun prepBatches(week: Int): List<PrepBatch> = MealSlot.entries.mapNotNull { slot ->
        val meal = scheduled(slot, week)
        val r = meal.recipe ?: return@mapNotNull null
        PrepBatch(r, kotlin.math.ceil(7 * meal.recipeServings / r.servings * 2) / 2, meal)
    }

    /** When to eat (docs/evidence/nutrition.md, T1–T5). */
    val timing: List<Pair<String, String>> = listOf(
        "The clock matters far less than the total" to "Meal timing and frequency \"appear to have little effect on fat loss or " +
            "lean mass retention\" (Helms 2014); a meta-analysis found no reliable benefit from more frequent meals " +
            "(Schoenfeld 2015). Hitting the day's calories and protein is what does the cutting.",
        "Protein in every meal" to "The position stand suggests 20–40 g per meal, every 3–4 hours (Jäger 2017). With three " +
            "meals and no snacks yours are bigger (about 50–80 g) and further apart; that's fine for fat loss, since " +
            "timing and frequency appear to have little effect on it (Helms 2014).",
        "Carbs around your run" to "Eat your fuel servings in the hours before the run and straight after it. On runs over " +
            "an hour take 30–60 g of carbohydrate per hour, and have a protein meal within 2 hours of finishing (Vitale 2019).",
        "Fasted morning runs don't burn more fat" to "Fat loss was the same fasted or fed in a 4-week trial on a calorie " +
            "deficit (Schoenfeld 2014; 20 women). Run fasted only if you prefer it, and only for short easy runs.",
        "An eating window is optional" to "Eating within a set daily window lost about 1.5 kg more fat than the same calories " +
            "without a window in a 2026 meta-analysis — but it also lost 0.4 kg more lean mass, and the trials were in " +
            "overweight non-athletes. Not built into this plan.",
    )

    /** Calories in a week's three meals. */
    fun baseKcal(week: Int): Int = MealSlot.entries.sumOf { scheduled(it, week).macros.kcal }.roundToInt()

    fun target(p: NutritionProfile, day: PlannedDay): DayTarget {
        val baseKcal = baseKcal(day.week)
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

    /**
     * Everything for one plan week: each recipe's ingredients times the batches cooked on prep day, the plain foods
     * eaten with the meals, and that week's fuel servings. Items with the same name and unit are added together.
     */
    fun shoppingList(p: NutritionProfile, weekDays: List<PlannedDay>): List<ShopItem> {
        val week = weekDays.firstOrNull()?.week ?: return emptyList()
        data class Key(val item: String, val unit: String, val dept: Dept)
        val recipeAmounts = linkedMapOf<Key, Double?>()
        for (b in prepBatches(week)) for (i in b.recipe.ingredients) {
            val dept = i.dept ?: continue
            val k = Key(i.item, if (i.unit == "cups") "cup" else i.unit, dept)
            recipeAmounts[k] = if (i.amount == null || (k in recipeAmounts && recipeAmounts[k] == null)) null
            else (recipeAmounts[k] ?: 0.0) + i.amount * b.batches
        }
        val grams = linkedMapOf<String, Int>()
        val firstOf = mutableMapOf<String, Ingredient>()
        fun add(i: Ingredient, times: Int = 1) {
            grams[i.foodId] = (grams[i.foodId] ?: 0) + i.grams * times
            firstOf.putIfAbsent(i.foodId, i)
        }
        var fuel = 0
        for (d in weekDays) {
            MealSlot.entries.forEach { s -> scheduled(s, d.week).items.forEach { add(it) } }
            fuel += target(p, d).fuelServings
        }
        // Spread the fuel servings evenly over the four options.
        fuelOptions.forEachIndexed { i, m -> m.items.forEach { add(it, (fuel + fuelOptions.size - 1 - i) / fuelOptions.size) } }
        // Teaspoons fold into tablespoons when the same item is also measured in tablespoons (3 tsp = 1 Tbsp).
        for (k in recipeAmounts.keys.filter { it.unit == "tsp" }.toList()) {
            val tbsp = Key(k.item, "Tbsp", k.dept)
            val tsp = recipeAmounts[k]
            if (tbsp in recipeAmounts && tsp != null && recipeAmounts[tbsp] != null) {
                recipeAmounts[tbsp] = recipeAmounts.getValue(tbsp)!! + tsp / 3
                recipeAmounts.remove(k)
            }
        }
        // Things bought whole (a count, cloves, cans) round up: you can't buy half an onion.
        fun whole(unit: String) = unit.isEmpty() || unit == "clove" || unit == "head" || unit.startsWith("can")
        val fromRecipes = recipeAmounts.map { (k, amt) ->
            val a = amt?.let { if (whole(k.unit)) kotlin.math.ceil(it - 1e-9) else it }
            ShopItem("r:${k.item}:${k.unit}", k.item, if (a == null) "to taste" else unitAmount(a, k.unit), k.dept)
        }
        val plain = grams.filterValues { it > 0 }.map { (id, g) ->
            val i = firstOf.getValue(id)
            val amount = i.unitGrams?.let { "${amountText(g.toDouble() / it)} ${i.unitName}" } ?: shopAmount(g)
            ShopItem("f:$id", i.label, amount, i.dept)
        }
        return (fromRecipes + plain).sortedBy { it.dept.ordinal }
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

/** 2.5 → "2½", 0.25 → "¼", 1.125 → "1⅛": nearest eighth, as recipes write it. */
fun amountText(x: Double): String {
    val eighths = kotlin.math.round(x * 8).toInt()
    val whole = eighths / 8
    val frac = when (eighths % 8) { 1 -> "⅛"; 2 -> "¼"; 3 -> "⅜"; 4 -> "½"; 5 -> "⅝"; 6 -> "¾"; 7 -> "⅞"; else -> "" }
    return if (whole == 0 && frac.isNotEmpty()) frac else "$whole$frac"
}

/** "2¼ lb", "3 cups", "1½ Tbsp", "8" (a count). */
fun unitAmount(x: Double, unit: String): String {
    val n = amountText(x)
    val u = when {
        unit == "cup" && x > 1.0 -> "cups"
        unit == "cups" && x <= 1.0 -> "cup"
        unit == "clove" && x > 1.0 -> "cloves"
        unit == "head" && x > 1.0 -> "heads"
        unit.startsWith("can") && x > 1.0 -> unit.replaceFirst("can", "cans")
        else -> unit
    }
    return if (u.isEmpty()) n else "$n $u"
}
