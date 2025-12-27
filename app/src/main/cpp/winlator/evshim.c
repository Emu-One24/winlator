/* evshim.c - Multi-Controller & Dynamic SDL Virtual Joystick Shim
 * Creates virtual SDL joysticks backed by shared memory for Wine controller
 * support
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

/* SDL2 types - minimal forward declarations */
typedef struct SDL_Joystick SDL_Joystick;
typedef struct {
  int major, minor, patch;
} SDL_version;
typedef struct SDL_VirtualJoystickDesc {
  uint16_t version;
  uint16_t type;
  uint16_t naxes;
  uint16_t nbuttons;
  uint16_t nhats;
  uint16_t vendor_id;
  uint16_t product_id;
  uint16_t padding;
  uint32_t button_mask;
  uint32_t axis_mask;
  const char *name;
  void *userdata;
  void (*Update)(void *);
  void (*SetPlayerIndex)(void *, int);
  int (*Rumble)(void *, uint16_t, uint16_t);
  int (*RumbleTriggers)(void *, uint16_t, uint16_t);
  int (*SetLED)(void *, uint8_t, uint8_t, uint8_t);
  int (*SendEffect)(void *, const void *, int);
} SDL_VirtualJoystickDesc;

#define SDL_VIRTUAL_JOYSTICK_DESC_VERSION 1
#define SDL_JOYSTICK_TYPE_GAMECONTROLLER 1
#define SDL_INIT_JOYSTICK 0x00000200

static int g_debug_enabled = 0;
#define LOGI(...) dprintf(STDOUT_FILENO, __VA_ARGS__)
#define LOGE(...) dprintf(STDERR_FILENO, __VA_ARGS__)
#define LOGD(...)                                                              \
  do {                                                                         \
    if (g_debug_enabled)                                                       \
      dprintf(STDOUT_FILENO, __VA_ARGS__);                                     \
  } while (0)

#define MAX_GAMEPADS 4
static int vjoy_ids[MAX_GAMEPADS] = {-1, -1, -1, -1};
static int read_fd[MAX_GAMEPADS] = {-1, -1, -1, -1};
static int rumble_fd[MAX_GAMEPADS] = {-1, -1, -1, -1};
static void *handle = NULL;
static pthread_mutex_t shm_mutex = PTHREAD_MUTEX_INITIALIZER;

struct gamepad_io {
  int16_t lx, ly, rx, ry, lt, rt;
  uint8_t btn[15];
  uint8_t hat;
  uint8_t _padding[4];
  uint16_t low_freq_rumble;
  uint16_t high_freq_rumble;
};

/* SDL function pointers */
static int (*p_SDL_Init)(uint32_t);
static const char *(*p_SDL_GetError)(void);
static SDL_Joystick *(*p_SDL_JoystickOpen)(int);
static int (*p_SDL_JoystickAttachVirtualEx)(const SDL_VirtualJoystickDesc *);
static int (*p_SDL_JoystickSetVirtualAxis)(SDL_Joystick *, int, int16_t);
static int (*p_SDL_JoystickSetVirtualButton)(SDL_Joystick *, int, uint8_t);
static int (*p_SDL_JoystickSetVirtualHat)(SDL_Joystick *, int, uint8_t);
static void (*p_SDL_PumpEvents)(void);
static void (*p_SDL_Delay)(uint32_t);
static void (*p_SDL_GetVersion)(SDL_version *);

#define GETFUNCPTR(name)                                                       \
  do {                                                                         \
    if (!(p_##name = (typeof(p_##name))dlsym(handle, #name)))                  \
      LOGE("Failed to load SDL: %s\n", #name);                                 \
  } while (0)

static int OnRumble(void *userdata, uint16_t low, uint16_t high) {
  int idx = (int)(intptr_t)userdata;
  if (idx < 0 || idx >= MAX_GAMEPADS || rumble_fd[idx] < 0)
    return -1;
  uint16_t vals[2] = {low, high};
  pthread_mutex_lock(&shm_mutex);
  pwrite(rumble_fd[idx], vals, sizeof(vals), 32);
  pthread_mutex_unlock(&shm_mutex);
  return 0;
}

static void *vjoy_updater(void *arg) {
  int idx = (int)(intptr_t)arg;
  int fd = read_fd[idx];
  if (fd < 0)
    return NULL;

  SDL_Joystick *js = p_SDL_JoystickOpen(vjoy_ids[idx]);
  if (!js) {
    LOGE("P%d: SDL_JoystickOpen failed\n", idx);
    return NULL;
  }

  struct gamepad_io cur, last = {0};
  LOGI("VJOY P%d running (PID %d)\n", idx, getpid());

  for (;;) {
    pthread_mutex_lock(&shm_mutex);
    ssize_t n = read(fd, &cur, sizeof cur);
    if (n == sizeof cur && memcmp(&cur, &last, sizeof cur) != 0) {
      p_SDL_JoystickSetVirtualAxis(js, 0, cur.lx);
      p_SDL_JoystickSetVirtualAxis(js, 1, cur.ly);
      p_SDL_JoystickSetVirtualAxis(js, 2, cur.rx);
      p_SDL_JoystickSetVirtualAxis(js, 3, cur.ry);
      p_SDL_JoystickSetVirtualAxis(js, 4, cur.lt);
      p_SDL_JoystickSetVirtualAxis(js, 5, cur.rt);
      for (int i = 0; i < 15; ++i)
        p_SDL_JoystickSetVirtualButton(js, i, cur.btn[i]);
      p_SDL_JoystickSetVirtualHat(js, 0, cur.hat);
      last = cur;
    }
    pthread_mutex_unlock(&shm_mutex);
    p_SDL_Delay(5);
  }
  return NULL;
}

__attribute__((constructor)) static void initialize_all_pads(void) {
  const char *dbg = getenv("EVSHIM_DEBUG");
  g_debug_enabled = dbg && strchr("1yY", *dbg);

  LOGI("EVSHIM initializing...\n");

  handle = dlopen("libSDL2-2.0.so.0", RTLD_LAZY | RTLD_GLOBAL);
  if (!handle) {
    LOGE("dlopen SDL failed: %s\n", dlerror());
    return;
  }

  GETFUNCPTR(SDL_Init);
  GETFUNCPTR(SDL_GetError);
  GETFUNCPTR(SDL_JoystickOpen);
  GETFUNCPTR(SDL_JoystickAttachVirtualEx);
  GETFUNCPTR(SDL_JoystickSetVirtualAxis);
  GETFUNCPTR(SDL_JoystickSetVirtualButton);
  GETFUNCPTR(SDL_JoystickSetVirtualHat);
  GETFUNCPTR(SDL_PumpEvents);
  GETFUNCPTR(SDL_Delay);
  GETFUNCPTR(SDL_GetVersion);

  p_SDL_Init(SDL_INIT_JOYSTICK);

  SDL_version v;
  p_SDL_GetVersion(&v);
  LOGI("SDL %d.%d.%d bound\n", v.major, v.minor, v.patch);

  int players =
      getenv("EVSHIM_MAX_PLAYERS") ? atoi(getenv("EVSHIM_MAX_PLAYERS")) : 1;
  if (players > MAX_GAMEPADS)
    players = MAX_GAMEPADS;

  // Get data path from environment or use default
  const char *data_path = getenv("EVSHIM_DATA_PATH");
  if (!data_path)
    data_path = "/data/data/com.winlator.cmod/files/imagefs/tmp";

  for (int i = 0; i < players; ++i) {
    char path[256];
    snprintf(path, sizeof path, "%s/gamepad%s.mem", data_path,
             (i == 0) ? "" : (char[2]){'0' + i, '\0'});

    read_fd[i] = open(path, O_RDONLY);
    rumble_fd[i] = open(path, O_WRONLY);

    if (read_fd[i] < 0 || rumble_fd[i] < 0) {
      LOGE("P%d: failed to open '%s': %s\n", i, path, strerror(errno));
      continue;
    }

    SDL_VirtualJoystickDesc d = {0};
    d.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
    d.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
    d.naxes = 6;
    d.nbuttons = 15;
    d.nhats = 1;
    d.Rumble = &OnRumble;
    d.userdata = (void *)(intptr_t)i;

    char name[64];
    snprintf(name, sizeof name, "Virtual Gamepad P%d", i + 1);
    d.name = strdup(name);

    vjoy_ids[i] = p_SDL_JoystickAttachVirtualEx(&d);
    if (vjoy_ids[i] < 0) {
      LOGE("P%d: SDL attach failed\n", i);
      continue;
    }

    pthread_t tid;
    pthread_create(&tid, NULL, vjoy_updater, (void *)(intptr_t)i);
    pthread_detach(tid);
  }
}

/* Hide /dev/input/event* to prevent conflicts */
static inline int is_event_node(const char *p) {
  return p && !strncmp(p, "/dev/input/event", 16);
}

typedef int (*open_f)(const char *, int, ...);
static open_f real_open;

int open(const char *path, int flags, ...)
    __attribute__((visibility("default")));
int open(const char *path, int flags, ...) {
  if (is_event_node(path)) {
    errno = ENOENT;
    return -1;
  }
  if (!real_open)
    real_open = (open_f)dlsym(RTLD_NEXT, "open");
  va_list ap;
  va_start(ap, flags);
  mode_t mode = (flags & O_CREAT) ? va_arg(ap, mode_t) : 0;
  va_end(ap);
  return real_open(path, flags, mode);
}
