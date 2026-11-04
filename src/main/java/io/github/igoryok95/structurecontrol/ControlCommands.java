package io.github.igoryok95.structurecontrol;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.time.Instant;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

public final class ControlCommands {
    private ControlCommands() {}
    @FunctionalInterface private interface Positioned { int run(CommandContext<CommandSourceStack> context, BlockPos pos); }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("structurecontrol").requires(source -> source.hasPermission(2))
                .executes(context -> help(context.getSource()));
        root.then(positioned(Commands.literal("info"), ControlCommands::info));
        var structure = Commands.argument("structure", ResourceLocationArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                        context.getSource().getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).keySet(), builder));
        structure.then(positioned(Commands.literal("off"), (context, pos) -> spawns(context, pos, false)));
        structure.then(positioned(Commands.literal("on"), (context, pos) -> spawns(context, pos, true)));
        root.then(Commands.literal("spawns").then(structure));
        var unregister = Commands.argument("structure", ResourceLocationArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                        context.getSource().getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).keySet(), builder))
                .executes(context -> guard(context.getSource(), () -> preview(context, playerPosition(context.getSource()))))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> guard(context.getSource(), () -> preview(context, BlockPosArgument.getBlockPos(context, "pos")))));
        root.then(Commands.literal("unregister").then(unregister));
        root.then(Commands.literal("confirm").then(Commands.argument("token", StringArgumentType.word())
                .executes(context -> guard(context.getSource(), () -> {
                    var source = context.getSource();
                    var key = service(source).confirm(actor(source), StringArgumentType.getString(context, "token"), source.getTextName());
                    source.sendSuccess(() -> Messages.tr(source, "unregistered", key.structure().toString(), key.chunkX(), key.chunkZ()), false);
                    return 1;
                }))));
        root.then(Commands.literal("cancel").executes(context -> {
            var source = context.getSource();
            service(source).cancel(actor(source));
            source.sendSuccess(() -> Messages.tr(source, "cancelled"), false);
            return 1;
        }));
        root.then(Commands.literal("undo").executes(context -> guard(context.getSource(), () -> {
            var source = context.getSource();
            Change change = service(source).undo(source.getTextName());
            source.sendSuccess(() -> Messages.tr(source, "undone", change.key().structure().toString()), false);
            return 1;
        })));
        root.then(Commands.literal("history").executes(context -> history(context.getSource(), 5))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, ControlData.HISTORY_LIMIT))
                        .executes(context -> history(context.getSource(), IntegerArgumentType.getInteger(context, "limit")))));
        root.then(Commands.literal("list").executes(context -> list(context.getSource())));
        root.then(Commands.literal("map").executes(context -> guard(context.getSource(), () -> {
            if (!(context.getSource().getEntity() instanceof ServerPlayer player)) throw new ControlFailure("error.player_required");
            ControlNetwork.open(player);
            return 1;
        })));
        var node = dispatcher.register(root);
        dispatcher.register(Commands.literal("structctl").requires(source -> source.hasPermission(2)).redirect(node));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> positioned(LiteralArgumentBuilder<CommandSourceStack> command, Positioned action) {
        return command.executes(context -> guard(context.getSource(), () -> action.run(context, playerPosition(context.getSource()))))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> guard(context.getSource(), () -> action.run(context, BlockPosArgument.getBlockPos(context, "pos")))));
    }

    private static BlockPos playerPosition(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer)) throw new ControlFailure("error.position_required");
        return BlockPos.containing(source.getPosition());
    }

    public static UUID actor(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : new UUID(0, 0);
    }
    private static StructureService service(CommandSourceStack source) { return StructureControl.service(source.getServer()); }

    public static int guard(CommandSourceStack source, IntSupplier operation) {
        try { return operation.getAsInt(); }
        catch (ControlFailure failure) {
            source.sendFailure(Messages.tr(source, failure.key(), failure.arguments()));
            return 0;
        }
    }

    private static int help(CommandSourceStack source) {
        for (String key : new String[]{"help.title", "help.info", "help.spawns", "help.unregister", "help.confirm", "help.undo", "help.history", "help.list", "help.map"}) {
            source.sendSuccess(() -> Messages.tr(source, key), false);
        }
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> context, BlockPos pos) {
        var source = context.getSource();
        var service = service(source);
        var found = service.inspect(source.getLevel(), pos, null);
        source.sendSuccess(() -> Messages.tr(source, "info.header", pos.getX(), pos.getY(), pos.getZ(), source.getLevel().dimension().location().toString()), false);
        if (found.isEmpty()) source.sendSuccess(() -> Messages.tr(source, "info.none"), false);
        for (var entry : found) {
            source.sendSuccess(() -> Messages.tr(source, "info.structure", entry.key().structure().toString(), entry.key().chunkX(), entry.key().chunkZ(),
                    Messages.tr(source, service.data().isDisabled(entry.key()) ? "state.off" : "state.on")), false);
            var box = entry.start().getBoundingBox();
            source.sendSuccess(() -> Messages.tr(source, "info.bounds", box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()), false);
            if (entry.structure().spawnOverrides().isEmpty()) source.sendSuccess(() -> Messages.tr(source, "info.no_overrides"), false);
            entry.structure().spawnOverrides().forEach((category, override) -> {
                String mobs = override.spawns().unwrap().stream().map(data -> BuiltInRegistries.ENTITY_TYPE.getKey(data.type).toString()).distinct().collect(Collectors.joining(", "));
                source.sendSuccess(() -> Messages.tr(source, "info.override", Messages.tr(source, "category." + category.getName()), mobs), false);
            });
        }
        return found.size();
    }

    private static int spawns(CommandContext<CommandSourceStack> context, BlockPos pos, boolean enabled) {
        var source = context.getSource();
        var service = service(source);
        var entry = service.select(source.getLevel(), pos, ResourceLocationArgument.getId(context, "structure"));
        service.setSpawns(entry, enabled, source.getTextName());
        source.sendSuccess(() -> Messages.tr(source, "spawns.changed", entry.key().structure().toString(), entry.key().chunkX(), entry.key().chunkZ(),
                Messages.tr(source, enabled ? "state.on" : "state.off")), false);
        return 1;
    }

    private static int preview(CommandContext<CommandSourceStack> context, BlockPos pos) {
        var source = context.getSource();
        var service = service(source);
        var located = service.select(source.getLevel(), pos, ResourceLocationArgument.getId(context, "structure"));
        var pending = service.preview(located, actor(source));
        source.sendSuccess(() -> Messages.tr(source, "preview.warning", located.key().structure().toString(), located.key().chunkX(), located.key().chunkZ(), ControlData.HISTORY_LIMIT)
                .withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Messages.tr(source, "preview.confirm", pending.token()).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/structurecontrol confirm " + pending.token()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Messages.tr(source, "preview.hover")))), false);
        return 1;
    }

    private static int history(CommandSourceStack source, int limit) {
        var changes = service(source).data().history();
        if (changes.isEmpty()) source.sendSuccess(() -> Messages.tr(source, "history.empty"), false);
        for (int i = changes.size() - 1; i >= Math.max(0, changes.size() - limit); i--) {
            Change change = changes.get(i);
            String action = change.kind() == Change.Kind.UNREGISTER ? "action.unregister" : change.afterDisabled() ? "action.spawns_off" : "action.spawns_on";
            source.sendSuccess(() -> Messages.tr(source, "history.entry", Messages.tr(source, action), change.key().structure().toString(), change.key().dimension().toString(),
                    change.key().chunkX(), change.key().chunkZ(), change.actor(), Instant.ofEpochMilli(change.timestamp()).toString()), false);
        }
        return Math.min(limit, changes.size());
    }

    private static int list(CommandSourceStack source) {
        var keys = service(source).data().disabledKeys();
        source.sendSuccess(() -> Messages.tr(source, "list.header", keys.size()), false);
        keys.stream().sorted(java.util.Comparator.comparing(StructureKey::toString)).limit(100).forEach(key ->
                source.sendSuccess(() -> Messages.tr(source, "list.entry", key.structure().toString(), key.dimension().toString(), key.chunkX(), key.chunkZ()), false));
        if (keys.size() > 100) source.sendSuccess(() -> Messages.tr(source, "list.truncated"), false);
        return keys.size();
    }
}
