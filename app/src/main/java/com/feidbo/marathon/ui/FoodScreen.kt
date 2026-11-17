package com.feidbo.marathon.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feidbo.marathon.core.Game
import com.feidbo.marathon.core.Meal
import com.feidbo.marathon.core.MealSlot
import com.feidbo.marathon.core.Nutrition
import com.feidbo.marathon.core.NutritionProfile
import com.feidbo.marathon.core.Plan
import com.feidbo.marathon.core.fmt
import com.feidbo.marathon.core.formatMiles
import com.feidbo.marathon.core.shortDate
import com.feidbo.marathon.ui.theme.StatusColors
import kotlin.math.roundToInt

/** What the Food tab can change. */
class FoodActions(
    val onProfile: (NutritionProfile) -> Unit,
    val onToggleMeal: (Int, String) -> Unit,
    val onToggleShop: (Int, String) -> Unit,
    val onWeighIn: (Int, Double) -> Unit,
)

private const val DISCLAIMER = "An estimate, not a dietitian's plan. Calories come from an equation and an assumed activity " +
    "level, and are corrected from your weekly weigh-in. If your running suffers or you feel run down, eat more."

@Composable
fun FoodScreen(state: UiState, actions: FoodActions) {
    val profile = state.data.nutrition
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Food", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        if (!profile.enabled) {
            SectionCard {
                Text("🥗 Meal plan for getting leaner", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Prep-ahead meals with a daily check-off and a weekly shopping list. About ${profile.deficitPct}% under " +
                        "maintenance on training-block days (roughly 1 lb a week), none in the taper or on race days, and " +
                        "about ${Nutrition.proteinTarget(profile)} g of protein a day to keep muscle.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                )
                Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { actions.onProfile(profile.copy(enabled = true)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Turn on the meal plan")
                }
            }
            ProfileCard(profile, actions)
            return@Column
        }
        val sd = state.schedule.on(state.today)
        if (sd == null) {
            SectionCard { Text("The meal plan follows your training plan (${shortDate(Plan.start)} – ${shortDate(Plan.raceDay)}).") }
            ProfileCard(profile, actions)
            return@Column
        }
        val day = sd.day
        val target = Nutrition.target(profile, day)
        val checks = state.data.mealChecks[day.index].orEmpty()

        SectionCard {
            Text("Today's target", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${target.kcal}", "kcal", Modifier.weight(1f))
                Stat("${target.proteinG} g", "protein", Modifier.weight(1f))
                Stat("${target.fuelServings}", "fuel servings", Modifier.weight(1f))
            }
            Text(
                "Maintenance today is about ${target.maintenanceKcal} kcal" +
                    (if (day.run.isRun) " (including ${formatMiles(day.miles)} mi of running)" else "") + ". ${target.note}",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
            )
            val low = target.energyAvailability < Nutrition.LOW_ENERGY_AVAILABILITY
            Text(
                "Energy availability: ${fmt("%.0f", target.energyAvailability)} kcal per kg of lean mass " +
                    "(warning line ${Nutrition.LOW_ENERGY_AVAILABILITY.roundToInt()}).",
                style = MaterialTheme.typography.labelMedium,
                color = if (low) StatusColors.warn else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's meals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("all four: +${Game.MEALS_XP} XP", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MealSlot.entries.forEach { slot ->
                val meal = Nutrition.scheduled(slot, day.week)
                val other = Nutrition.mealsFor(slot).first { it.id != meal.id }
                MealRow(slot, meal, other, slot.name in checks) { actions.onToggleMeal(day.index, slot.name) }
            }
            if (target.fuelServings > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                Text("Fuel servings · ${target.fuelServings} today", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Extra carbohydrate for your run, about ${Nutrition.FUEL_KCAL} kcal each — best before or after it. " +
                        "Any of: " + Nutrition.fuelOptions.joinToString("; ") { it.name } + ".",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    (1..target.fuelServings.coerceAtMost(8)).forEach { n ->
                        val key = "fuel$n"
                        CheckIcon(key in checks, "Fuel serving $n") { actions.onToggleMeal(day.index, key) }
                    }
                }
            }
            Text("Recipes: Food Hero (Oregon State University Extension), nutrition from each recipe's label. Plain foods: USDA FoodData Central.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
        }

        TimingCard()
        PrepCard(day.week)

        // Shopping list: you shop and prep on the weekend for the week ahead, so Friday–Sunday it opens on next week.
        val thisWeek = sd.original.week
        val hasNext = state.schedule.days.any { it.original.week == thisWeek + 1 }
        var next by rememberSaveable(thisWeek) { mutableStateOf(hasNext && day.date.dayOfWeek >= java.time.DayOfWeek.FRIDAY) }
        val week = if (next && hasNext) thisWeek + 1 else thisWeek
        val weekDays = state.schedule.days.filter { it.original.week == week }.map { it.day }
        val shop = Nutrition.shoppingList(profile, weekDays)
        val ticked = state.data.shopChecks[week].orEmpty()
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Shopping list", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${shop.count { it.key in ticked }} / ${shop.size}", style = MaterialTheme.typography.labelLarge)
            }
            if (hasNext) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.FilterChip(selected = week == thisWeek, onClick = { next = false }, label = { Text("This week") })
                    androidx.compose.material3.FilterChip(selected = week != thisWeek, onClick = { next = true }, label = { Text("Next week") })
                }
            }
            Text("For ${shortDate(weekDays.first().date)} – ${shortDate(weekDays.last().date)}: every meal plus that week's fuel " +
                "servings. Rice is listed as the cooked amount.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            var dept: com.feidbo.marathon.core.Dept? = null
            shop.forEach { item ->
                if (item.dept != dept) {
                    dept = item.dept
                    Text(item.dept.title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                }
                val done = item.key in ticked
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { actions.onToggleShop(week, item.key) }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle, contentDescription = null,
                        tint = if (done) StatusColors.done else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Text(item.amount, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        WeighInCard(state, sd.index, actions)
        ProfileCard(profile, actions)
        Text(DISCLAIMER, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun CheckIcon(done: Boolean, description: String, onClick: () -> Unit) {
    Icon(
        if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle, contentDescription = description,
        tint = if (done) StatusColors.done else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    )
}

private fun macroLine(m: Meal): String = m.macros.let {
    "${it.kcal.roundToInt()} kcal · ${it.protein.roundToInt()} g protein · ${it.carbs.roundToInt()} g carbs · ${it.fat.roundToInt()} g fat"
}

@Composable
private fun MealRow(slot: MealSlot, meal: Meal, other: Meal, done: Boolean, onToggle: () -> Unit) {
    var open by rememberSaveable(meal.id) { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { open = !open }) {
                Text("${slot.emoji}  ${slot.title}: ${meal.name}", style = MaterialTheme.typography.titleSmall)
                Text(macroLine(meal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (open) "Hide details" else if (meal.recipe != null) "Recipe, portion & travel swap" else "Details & travel swap",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            CheckIcon(done, if (done) "${slot.title} eaten" else "Mark ${slot.title} eaten", onToggle)
        }
        if (open) {
            Text("Your portion: ${meal.prep}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp))
            meal.items.forEach { i ->
                Text("•  with ${i.label}: ${i.grams} g" + (if (i.hint.isNotEmpty()) " (${i.hint})" else ""),
                    style = MaterialTheme.typography.bodySmall)
            }
            meal.recipe?.let { RecipeDetails(it) }
            Text("Travelling: ${meal.away}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text("Swap: ${other.name} (${macroLine(other)}) counts too.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The recipe exactly as Food Hero publishes it, with its source. */
@Composable
private fun RecipeDetails(r: com.feidbo.marathon.core.Recipe) {
    Column(Modifier.padding(top = 6.dp)) {
        Text("${r.title} — Food Hero, Oregon State University Extension", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Text("Makes ${r.makes} · prep ${r.prep} · cook ${r.cook} · label serving ${r.servingSize}: " +
            "${r.perServing.kcal.roundToInt()} kcal, ${fmt("%.0f", r.perServing.protein)} g protein",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Ingredients", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        r.ingredients.forEach { Text("•  ${it.line}", style = MaterialTheme.typography.bodySmall) }
        Text("Directions", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        r.steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall) }
        if (r.notes.isNotEmpty()) {
            Text("Notes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            r.notes.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
        }
        Text(r.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrepCard(week: Int) {
    var open by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text("🧑‍🍳  Prep once a week · Sunday", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (open) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (open) {
            Text("One session covers Monday to Sunday. Next week, cook:", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp))
            Nutrition.prepBatches(week + 1).forEach { b ->
                val made = b.batches * b.recipe.servings
                Text("${b.forMeal.slot.emoji}  ${b.recipe.title} × ${com.feidbo.marathon.core.amountText(b.batches)}",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                Text("${com.feidbo.marathon.core.amountText(b.batches)} times the recipe (${com.feidbo.marathon.core.amountText(made)} " +
                    "label servings) for 7 × ${b.forMeal.prep.removeSuffix(".")}",
                    style = MaterialTheme.typography.bodySmall)
                RecipeDetails(b.recipe)
            }
            Text(Nutrition.PREP_STORAGE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
            Text("The menu swaps every week: three recipes per session, the other three the week after.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimingCard() {
    var open by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text("⏰  When to eat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (open) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text("Protein in each of the four meals, 3–4 hours apart; fuel servings around your run.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (open) {
            Nutrition.timing.forEach { (title, text) ->
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WeighInCard(state: UiState, dayIndex: Int, actions: FoodActions) {
    var lb by rememberSaveable { mutableStateOf("") }
    val weighIns = state.data.weighIns.sortedBy { it.dayIndex }
    SectionCard {
        Text("⚖️  Weekly weigh-in", style = MaterialTheme.typography.titleMedium)
        Text("Same day each week, first thing in the morning. It corrects the calorie estimate and updates your targets.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            NumberField(lb, { lb = it }, "Weight", Modifier.weight(1f), suffix = "lb")
            Spacer(Modifier.width(8.dp))
            val value = lb.toDoubleOrNull()?.takeIf { it in 90.0..400.0 }
            OutlinedButton(onClick = { value?.let { actions.onWeighIn(dayIndex, it); lb = "" } }, enabled = value != null) { Text("Save") }
        }
        Nutrition.weighInAdvice(weighIns)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
        weighIns.takeLast(5).reversed().forEach { w ->
            Text("${shortDate(Plan.days[w.dayIndex].date)}: ${formatMiles(w.lb)} lb", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProfileCard(profile: NutritionProfile, actions: FoodActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    var weight by rememberSaveable(profile.weightLb) { mutableStateOf(formatMiles(profile.weightLb)) }
    var age by rememberSaveable(profile.age) { mutableStateOf(profile.age.toString()) }
    var fat by rememberSaveable(profile.bodyFatPct) { mutableStateOf(formatMiles(profile.bodyFatPct)) }
    var activity by rememberSaveable(profile.activityFactor) { mutableStateOf(profile.activityFactor.toString()) }
    var deficit by rememberSaveable(profile.deficitPct) { mutableStateOf(profile.deficitPct.toString()) }
    SectionCard {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text("Food settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (open) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text("${formatMiles(profile.weightLb)} lb · age ${profile.age} · ${formatMiles(profile.bodyFatPct)}% body fat · " +
            "resting ${Nutrition.restingKcal(profile).roundToInt()} kcal",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                NumberField(weight, { weight = it }, "Weight", Modifier.fillMaxWidth(), suffix = "lb")
                NumberField(age, { if (it.length <= 2) age = it }, "Age", Modifier.fillMaxWidth(), decimal = false)
                NumberField(fat, { fat = it }, "Body fat (rough is fine)", Modifier.fillMaxWidth(), suffix = "%")
                NumberField(activity, { activity = it }, "Daily activity outside training (× resting)", Modifier.fillMaxWidth())
                Text("1.4 is an assumption for a mostly seated job. Lower it if your weight isn't moving after two weeks; raise " +
                    "it if you're losing too fast.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField(deficit, { if (it.length <= 2) deficit = it }, "Deficit on cutting days", Modifier.fillMaxWidth(),
                    decimal = false, suffix = "%")
                Text("Slow loss (a 19% deficit, 0.7% of body weight a week) kept muscle better than fast loss in athletes " +
                    "(Garthe 2011). 0 turns the cut off and keeps the meals.", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        actions.onProfile(profile.copy(
                            weightLb = weight.toDoubleOrNull()?.takeIf { it in 90.0..400.0 } ?: profile.weightLb,
                            age = age.toIntOrNull()?.takeIf { it in 16..90 } ?: profile.age,
                            bodyFatPct = fat.toDoubleOrNull()?.takeIf { it in 4.0..50.0 } ?: profile.bodyFatPct,
                            activityFactor = activity.toDoubleOrNull()?.takeIf { it in 1.1..2.0 } ?: profile.activityFactor,
                            deficitPct = deficit.toIntOrNull()?.takeIf { it in 0..25 } ?: profile.deficitPct,
                        ))
                        open = false
                    }) { Text("Save") }
                    if (profile.enabled) {
                        TextButton(onClick = { actions.onProfile(profile.copy(enabled = false)) }) { Text("Turn the meal plan off") }
                    }
                }
            }
        }
    }
}
