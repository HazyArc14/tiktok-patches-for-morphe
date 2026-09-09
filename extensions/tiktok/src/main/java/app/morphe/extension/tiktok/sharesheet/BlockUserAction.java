package app.morphe.extension.tiktok.sharesheet;

import android.app.AlertDialog;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Adds a "Block" item to the video share sheet's Video Actions row, mirroring the Block action a
 * profile's share sheet already has.
 * <p>
 * TikTok's action types aren't on this module's compile classpath, so the item is a proxy standing
 * in for one. It answers only its own key, label, icon and click, and hands every other call to a
 * real action TikTok built for the same panel: the share sheet reads a lot off these items without
 * null-checking, so any value invented here risks bringing the panel down.
 */
public final class BlockUserAction {
    private static final String ACTION_INTERFACE_CLASS = "X.0oUL";
    private static final String TEMPLATE_ACTION_CLASS = "X.0olK";
    private static final String SHARE_PACKAGE_CLASS = "com.ss.android.ugc.aweme.share.improve.pkg.AwemeSharePackage";
    private static final String PROFILE_SERVICE_CLASS = "com.ss.android.ugc.aweme.profile.IProfileService";
    private static final String SERVICE_MANAGER_CLASS = "com.ss.android.ugc.aweme.framework.services.ServiceManager";
    private static final String BLOCK_API_CLASS = "com.ss.android.ugc.aweme.profile.api.BlockApi";
    private static final String BLOCK_SERVICE_CLASS = BLOCK_API_CLASS + "$BlockService";
    private static final String BLOCK_CALL_CLASS = "X.13jl";

    /**
     * Block state we've changed ourselves, by uid. The feed's copy of a user keeps reporting
     * whatever it was fetched with, in both directions, so this overrides it for the session.
     */
    private static final Map<String, Boolean> BLOCK_OVERRIDES = Collections.synchronizedMap(new HashMap<>());

    private BlockUserAction() {
    }

    @SuppressWarnings("unchecked")
    public static void inject(Object builder) {
        try {
            if (builder == null || !Settings.SHARE_SHEET_BLOCK_USER_ACTION.get()) {
                return;
            }

            Object rawList = findField(builder.getClass(), "LJFF").get(builder);
            if (!(rawList instanceof List)) {
                Logger.printDebug(() -> "Share sheet block action: LJFF was not a List, actual type="
                        + (rawList == null ? "null" : rawList.getClass().getName()));
                return;
            }
            List<Object> actions = (List<Object>) rawList;

            for (Object action : actions) {
                if (Proxy.isProxyClass(action.getClass())) {
                    return;
                }
            }

            Object sharePackage = findField(builder.getClass(), "LJJIIJ").get(builder);
            Author author = resolveAuthor(sharePackage);
            if (author == null) {
                return;
            }

            // Unblocking reaches the server but TikTok keeps rendering the old state, so the item
            // is simply not offered for someone already blocked rather than appearing to do nothing.
            if (author.blocked) {
                Logger.printDebug(() -> "Share sheet block action: uid=" + author.uid
                        + " already blocked, not injecting");
                return;
            }

            Object template = resolveTemplate(actions, builder.getClass().getClassLoader());
            if (template == null) {
                Logger.printDebug(() -> "Share sheet block action: no template action available, skipping");
                return;
            }

            List<String> borrowable = discoverBorrowableKeys(builder, template, actions);
            String actionKey = chooseActionKey(borrowable);
            if (actionKey == null) {
                Logger.printDebug(() -> "Share sheet block action: no borrowable key on this panel, skipping");
                return;
            }

            Object proxy = Proxy.newProxyInstance(
                    builder.getClass().getClassLoader(),
                    new Class<?>[]{Class.forName(ACTION_INTERFACE_CLASS, false, builder.getClass().getClassLoader())},
                    new BlockActionHandler(template, actionKey, author)
            );

            actions.add(0, proxy);
            Logger.printDebug(() -> "Share sheet block action: injected for uid=" + author.uid
                    + " under key=" + actionKey
                    + ", template=" + template.getClass().getName()
                    + ", borrowable=" + borrowable);
        } catch (Throwable t) {
            Logger.printException(() -> "Share sheet: block action injection failed", t);
        }
    }

    /**
     * The panel silently drops any action whose key isn't allow-listed for what's being shared, so
     * the item can only travel under a key that panel already accepts. Which keys those are varies
     * by post, and the panel's own predicate is the authority, so ask it. Keys the panel is already
     * using are excluded, since borrowing one of those would displace a real action.
     */
    private static List<String> discoverBorrowableKeys(Object builder, Object template, List<Object> actions) {
        Set<String> present = new LinkedHashSet<>();
        for (Object action : actions) {
            present.add(itemKey(action));
        }

        List<String> borrowable = new ArrayList<>();
        for (ShareSheetOptions.Option option : ShareSheetOptions.VIDEO_ACTIONS.allOptions()) {
            if (!present.contains(option.key) && panelAcceptsKey(builder, template, option.key)) {
                borrowable.add(option.key);
            }
        }

        return borrowable;
    }

    /**
     * Prefers a slot TikTok has never actually used on this installation, so the action doesn't
     * disappear on the posts where TikTok wants that slot back. "Save Photo", for instance, is free
     * on videos but genuinely used on photo posts.
     */
    private static String chooseActionKey(List<String> borrowable) {
        Set<String> everUsed = ShareSheetOptions.VIDEO_ACTIONS
                .parseKeys(Settings.SHARE_SHEET_ACTIONS_OBSERVED.get());
        for (String key : borrowable) {
            if (!everUsed.contains(key)) {
                return key;
            }
        }
        return borrowable.isEmpty() ? null : borrowable.get(0);
    }

    private static boolean panelAcceptsKey(Object builder, Object template, String key) {
        try {
            ClassLoader loader = builder.getClass().getClassLoader();
            Object probe = Proxy.newProxyInstance(
                    loader,
                    new Class<?>[]{Class.forName(ACTION_INTERFACE_CLASS, false, loader)},
                    (proxy, method, args) -> "key".equals(method.getName())
                            ? key
                            : method.invoke(template, args)
            );
            return Boolean.TRUE.equals(panelAccepts(builder, probe));
        } catch (Throwable t) {
            return false;
        }
    }

    private static String itemKey(Object action) {
        try {
            return String.valueOf(action.getClass().getMethod("key").invoke(action));
        } catch (Throwable t) {
            return "<no-key>";
        }
    }

    /**
     * A real action instance, used only as a source of valid resource ids and enums. Prefers one
     * TikTok already built for this panel, falling back to its copy-link action.
     */
    private static Object resolveTemplate(List<Object> actions, ClassLoader loader) {
        for (Object action : actions) {
            if (!Proxy.isProxyClass(action.getClass())) {
                return action;
            }
        }

        try {
            return Class.forName(TEMPLATE_ACTION_CLASS, false, loader).newInstance();
        } catch (Throwable t) {
            Logger.printException(() -> "Share sheet: block action template creation failed", t);
            return null;
        }
    }

    private static Author resolveAuthor(Object sharePackage) {
        if (sharePackage == null) {
            return null;
        }

        try {
            Class<?> sharePackageClass = Class.forName(SHARE_PACKAGE_CLASS, false, sharePackage.getClass().getClassLoader());
            if (!sharePackageClass.isInstance(sharePackage)) {
                return null;
            }

            Object aweme = sharePackageClass.getMethod("LJJI").invoke(sharePackage);
            if (aweme == null) {
                return null;
            }

            Object author = aweme.getClass().getMethod("getAuthor").invoke(aweme);
            if (author == null) {
                return null;
            }

            String uid = (String) author.getClass().getMethod("getUid").invoke(author);
            String secUid = (String) author.getClass().getMethod("getSecUid").invoke(author);
            if (uid == null || uid.isEmpty()) {
                return null;
            }

            String nickname;
            try {
                nickname = (String) author.getClass().getMethod("getNickname").invoke(author);
            } catch (Throwable ignored) {
                nickname = null;
            }

            return new Author(
                    uid,
                    secUid,
                    (nickname == null || nickname.isEmpty()) ? "this user" : nickname,
                    isBlocked(author, uid)
            );
        } catch (Throwable t) {
            Logger.printException(() -> "Share sheet: block action author lookup failed", t);
            return null;
        }
    }

    /**
     * The feed's copy of a user can still report the pre-block state after we've blocked them, so
     * a block we performed ourselves counts too.
     */
    private static boolean isBlocked(Object author, String uid) {
        Boolean override = BLOCK_OVERRIDES.get(uid);
        if (override != null) {
            return override;
        }

        try {
            Object blockStatus = author.getClass().getMethod("getBlockStatus").invoke(author);
            if (blockStatus instanceof Integer && (Integer) blockStatus != 0) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            return Boolean.TRUE.equals(author.getClass().getMethod("isBlock").invoke(author));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class Author {
        final String uid;
        final String secUid;
        final String nickname;
        final boolean blocked;

        Author(String uid, String secUid, String nickname, boolean blocked) {
            this.uid = uid;
            this.secUid = secUid;
            this.nickname = nickname;
            this.blocked = blocked;
        }
    }

    /** The panel drops any action its own {@code LJJ} predicate rejects, before anything renders. */
    private static Boolean panelAccepts(Object builder, Object action) {
        try {
            Object predicate = findField(builder.getClass(), "LJJ").get(builder);
            if (predicate == null) {
                return null;
            }
            Object accepted = predicate.getClass()
                    .getMethod("invoke", Object.class).invoke(predicate, action);
            return accepted instanceof Boolean ? (Boolean) accepted : null;
        } catch (Throwable t) {
            Logger.printDebug(() -> "Share sheet block action: predicate check failed (" + t + ")");
            return null;
        }
    }

    private static Field findField(Class<?> startClass, String fieldName) throws NoSuchFieldException {
        for (Class<?> clazz = startClass; clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static int rawResourceId(String name) {
        try {
            Context context = Utils.getContext();
            if (context == null) {
                return 0;
            }
            return context.getResources().getIdentifier(name, "raw", context.getPackageName());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static final class BlockActionHandler implements InvocationHandler {
        private final Object template;
        private final String actionKey;
        private final Author author;
        private long lastClickUptime;

        BlockActionHandler(Object template, String actionKey, Author author) {
            this.template = template;
            this.actionKey = actionKey;
            this.author = author;
        }



        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            try {
                switch (method.getName()) {
                    case "key":
                        return actionKey;
                    case "enable":
                        return Boolean.TRUE;
                    case "toString":
                        return "BlockUserAction[" + author.uid + "]";
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    case "LJIIJ":
                        return "Block";
                    case "LJIJ":
                        Logger.printDebug(() -> "Share sheet block action: binding label view");
                        ((TextView) args[0]).setText("Block");
                        return null;
                    case "LJII":
                        return iconId("icon_block_fill", method);
                    case "LJIIIIZZ":
                        return iconId("icon_2pt_block", method);
                    // Click path: LJJJJLI -> LJJLI -> LJJLIIIIJ -> LJIILIIL, plus LJJIIJ as the
                    // alternate branch of X.0oUM's dispatcher. Whichever TikTok calls, run once.
                    case "LJJJJLI":
                    case "LJJLI":
                    case "LJJIIJ":
                        onClick(((View) args[0]).getContext(), method.getName());
                        return null;
                    case "LJJLIIIIJ":
                    case "LJIILIIL":
                        onClick((Context) args[0], method.getName());
                        return null;
                    default:
                        return method.invoke(template, args);
                }
            } catch (Throwable t) {
                Logger.printException(() -> "Share sheet block action: " + method.getName() + " failed", t);
                return defaultFor(method.getReturnType());
            }
        }

        private Object iconId(String resourceName, Method method) throws Exception {
            int id = rawResourceId(resourceName);
            return id != 0 ? id : method.invoke(template);
        }

        private void onClick(Context context, String entryPoint) {
            long now = SystemClock.uptimeMillis();
            if (now - lastClickUptime < 1000L) {
                return;
            }
            lastClickUptime = now;

            Logger.printDebug(() -> "Share sheet block action: clicked via " + entryPoint
                    + ", uid=" + author.uid);
            new AlertDialog.Builder(context)
                    .setTitle("Block " + author.nickname + "?")
                    .setMessage("They won't be able to find your profile, videos, or comments, and you won't see theirs.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Block", (dialog, which) -> executeBlock(context))
                    .show();
        }

        /**
         * Blocks via the endpoint TikTok's own blocked-accounts screen uses rather than
         * {@code IProfileService.blockUser}, which ignores the block_type it's handed. The response
         * reports the resulting state, so the outcome is confirmed before it's claimed to the user.
         */
        private void executeBlock(Context context) {
            Utils.runOnBackgroundThread(() -> {
                try {
                    ClassLoader loader = context.getClassLoader();
                    Field field = Class.forName(BLOCK_API_CLASS, true, loader).getDeclaredField("LIZ");
                    field.setAccessible(true);
                    Object service = field.get(null);

                    Object call = Class.forName(BLOCK_SERVICE_CLASS, false, loader)
                            .getMethod("block", String.class, String.class, int.class, int.class)
                            .invoke(service, author.uid, author.secUid, 1, 0);
                    Object response = Class.forName(BLOCK_CALL_CLASS, false, loader)
                            .getMethod("execute").invoke(call);

                    Object body = response == null ? null : response.getClass().getField("LIZIZ").get(response);
                    Object blockStatus = body == null
                            ? null
                            : body.getClass().getMethod("getBlockStatus").invoke(body);

                    // blockStatus alone can't be trusted: 0 is both "not blocked" and the default
                    // for a body the server never populated. The BaseResponse status decides.
                    Object statusCode = responseField(body, "status_code");
                    boolean applied = Integer.valueOf(0).equals(statusCode)
                            && Integer.valueOf(1).equals(blockStatus);

                    Logger.printDebug(() -> "Share sheet block action: block uid=" + author.uid
                            + " blockStatus=" + blockStatus
                            + " status_code=" + statusCode
                            + " status_msg=" + responseField(body, "status_msg"));

                    if (applied) {
                        BLOCK_OVERRIDES.put(author.uid, Boolean.TRUE);
                        invalidateUserCache(loader);
                    }
                    Utils.runOnMainThread(() -> Utils.showToastShort(applied
                            ? "Blocked " + author.nickname
                            : "Couldn't block " + author.nickname));
                } catch (Throwable t) {
                    Throwable cause = t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t;
                    Logger.printException(() -> "Share sheet: block call failed: " + cause, cause);
                    Utils.runOnMainThread(() -> Utils.showToastShort("Couldn't block " + author.nickname));
                }
            });
        }

        /**
         * Going straight to the endpoint changes the server state but leaves TikTok holding the
         * user it fetched earlier, so the profile keeps rendering the old block state until the app
         * restarts. Dropping the cached copy makes the next read fetch it again.
         */
        private void invalidateUserCache(ClassLoader loader) {
            try {
                Class<?> serviceManagerClass = Class.forName(SERVICE_MANAGER_CLASS, false, loader);
                Class<?> profileServiceClass = Class.forName(PROFILE_SERVICE_CLASS, false, loader);
                Object profileService = serviceManagerClass.getMethod("getService", Class.class)
                        .invoke(serviceManagerClass.getMethod("get").invoke(null), profileServiceClass);

                profileServiceClass.getMethod("removeCacheByUser", String.class, String.class)
                        .invoke(profileService, author.uid, author.secUid);

                Logger.printDebug(() -> "Share sheet block action: dropped cached user " + author.uid);
            } catch (Throwable t) {
                Throwable cause = t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t;
                Logger.printException(() -> "Share sheet: could not drop cached user: " + cause, cause);
            }
        }

        private static Object responseField(Object body, String name) {
            try {
                return body == null ? null : body.getClass().getField(name).get(body);
            } catch (Throwable t) {
                return "<" + name + " unavailable>";
            }
        }

        private static Object defaultFor(Class<?> returnType) {
            if (returnType == boolean.class) {
                return Boolean.FALSE;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == float.class) {
                return 0f;
            }
            return null;
        }
    }
}
