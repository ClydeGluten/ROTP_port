package com.github.standobyte.jojo.fabric.stand.crazydiamond;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/** CrazyDiamondPreviousState.convertTo: actual server recipes, source category priority and exclusions. */
public final class RecipeReversal {
    public record Result(List<ItemStack> ingredients, int consumedCount) { }
    private record Candidate(Recipe<?> recipe, ItemStack output, int priority) { }
    private static final Result EXISTS = new Result(List.of(), 0);

    public static Optional<Result> convertTo(ItemStack stack, ServerLevel level, Predicate<Recipe<?>> additionalCondition,
            RandomSource random, boolean createItems) {
        if (stack.isEmpty()) return Optional.empty();
        if (stack.is(Items.ENCHANTED_BOOK)) return Optional.of(createItems
                ? new Result(List.of(new ItemStack(Items.BOOK)), 1) : EXISTS);
        if (stack.is(Items.WRITTEN_BOOK)) {
            if (!createItems) return Optional.of(EXISTS);
            var book = new ItemStack(Items.WRITABLE_BOOK);
            var written = stack.getOrDefault(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY);
            book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(written.pages().stream()
                    .map(page -> Filterable.passThrough(page.raw().getString())).toList()));
            return Optional.of(new Result(List.of(book), 1));
        }
        var context = SlotDisplayContext.fromLevel(level);
        var candidates = new ArrayList<Candidate>();
        for (var holder : level.recipeAccess().getRecipes()) {
            var recipe = holder.value();
            // Modern recipes expose their static result through the server's recipe display.
            // Dynamic recipes with no declared result/ingredients stay nonreversible, as in source.
            var output = recipe.display().stream().map(display -> display.result().resolveForFirstStack(context))
                    .filter(item -> !item.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
            if (output.getItem() != stack.getItem()) continue;
            if (recipe instanceof BlastingRecipe) return Optional.empty();
            if (output.getCount() > stack.getCount() || additionalCondition != null && !additionalCondition.test(recipe)) continue;
            candidates.add(new Candidate(recipe, output, priority(recipe)));
        }
        if (candidates.isEmpty()) return Optional.empty();
        int priority = candidates.stream().mapToInt(Candidate::priority).min().orElseThrow();
        var preferred = candidates.stream().filter(candidate -> candidate.priority == priority).toList();
        var chosen = preferred.get(random.nextInt(preferred.size()));
        var ingredients = getIngredients(chosen.recipe);
        return ingredients.isEmpty() ? Optional.empty() : Optional.of(new Result(ingredients, chosen.output.getCount()));
    }

    private static int priority(Recipe<?> recipe) {
        if (recipe instanceof SmithingRecipe) return 0;
        if (recipe instanceof AbstractCookingRecipe) return 1;
        if (recipe instanceof StonecutterRecipe) return 2;
        if (recipe instanceof CraftingRecipe) return 3;
        return 4;
    }

    public static List<ItemStack> getIngredients(Recipe<?> recipe) {
        var random = new Random(); // Original chooses ingredient alternatives independently from recipe selection.
        var result = new ArrayList<ItemStack>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            var choices = ingredient.items().toList();
            result.add(choices.isEmpty() ? ItemStack.EMPTY : new ItemStack(choices.get(random.nextInt(choices.size()))));
        }
        return result;
    }

    private RecipeReversal() { }
}
