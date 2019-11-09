package io.github.portlek.sign;

import io.github.portlek.packetlisten.Packets;
import io.github.portlek.reflection.RefClass;
import io.github.portlek.reflection.RefConstructed;
import io.github.portlek.reflection.RefField;
import io.github.portlek.reflection.RefMethod;
import io.github.portlek.reflection.clazz.ClassOf;
import io.github.portlek.versionmatched.Version;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.cactoos.iterable.Mapped;
import org.cactoos.list.ListOf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Sign {

    private static final String UPDATE_PACKET = "PacketPlayInUpdateSign";

    private static final Packets PACKETS = new Packets();

    private final Map<Player, Menu> inputReceivers = new HashMap<>();

    private final Map<Player, Location> signLocations = new HashMap<>();

    @NotNull
    private final Plugin plugin;

    public Sign(@NotNull Plugin plugin) {
        this.plugin = plugin;
        listen();
    }

    @NotNull
    public Menu newMenu(@NotNull Player player, @NotNull List<String> text) {
        final Menu menu = new Menu(player, text);

        menu.onOpen(location -> {
            signLocations.put(player, location);
            inputReceivers.putIfAbsent(player, menu);
        });

        return menu;
    }

    private void listen() {
        PACKETS.prepareFor(plugin);
        PACKETS.registerWrite(UPDATE_PACKET, event -> {
            final Player player = event.getPlayer();
            final Object packet = event.getPacket();
            final List<String> input = new ListOf<>(
                new Mapped<>(
                    object -> (String) chatComponentGetText.of(object).call(""),
                    updateSignFieldB.of(packet).get(packet)
                )
            );
            final Menu menu = inputReceivers.remove(player);
            final Location location = signLocations.remove(player);

            if (menu == null || location == null) {
                return;
            }

            event.setCancelled(true);

            final boolean success = menu.response().test(player, input);

            if (!success && menu.isReopenIfFail()) {
                Bukkit.getScheduler().runTaskLater(plugin, menu::open, 2L);
            }

            player.sendBlockChange(location, Material.AIR.createBlockData());
        });
    }

    private static final Version VERSION = new Version();

    private static RefMethod getHandle;
    private static RefField playerConnection;
    private static RefField networkManager;
    private static RefMethod sendPacket;
    private static RefField updateSignFieldB;
    private static RefMethod chatComponentGetText;
    static RefConstructed blockPosition;
    static RefField blockPositionX;
    static RefField blockPositionY;
    static RefField blockPositionZ;

    static {
        final String nms = "net.minecraft.server.v";

        try {
            getHandle = new ClassOf(nms + VERSION.raw() + ".entity.CraftPlayer").getMethod("getHandle");
            playerConnection = new ClassOf(nms + VERSION.raw() + ".EntityPlayer").getField("playerConnection");
            networkManager = new ClassOf(nms + VERSION.raw() + ".PlayerConnection").getField("networkManager");
            final RefClass packetClass = new ClassOf(nms + Version.class + ".Packet");
            sendPacket = new ClassOf(nms + VERSION.raw() + ".PlayerConnection")
                .getMethod("sendPacket", packetClass.getRealClass());
            updateSignFieldB = new ClassOf(nms + VERSION.raw() + UPDATE_PACKET).getField("b");
            chatComponentGetText = new ClassOf(nms + VERSION.raw() + "IChatBaseComponent")
                .getMethod("getText");
            blockPosition = new ClassOf(nms + VERSION.raw() + ".BlockPosition")
                .getConstructor(int.class, int.class, int.class);
            final RefClass baseBlockPosition = new ClassOf(nms + VERSION.raw() + ".BaseBlockPosition");
            blockPositionX = baseBlockPosition.getField("getX");
            blockPositionY = baseBlockPosition.getField("getY");
            blockPositionZ = baseBlockPosition.getField("getZ");
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    static void sendPacket(@NotNull Player player, @NotNull Object packet) {
        sendPacket.of(
            networkManager.of(
                playerConnection.of(
                    getHandle.of(player).call(player)
                ).get(player)
            ).get(player)
        ).call(player, packet);
    }

    @Nullable
    static Object createPacket(@NotNull String className, @NotNull Object... parameters) {
        try {
            return new ClassOf(className).getConstructor(parameters).create(null, parameters);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

}