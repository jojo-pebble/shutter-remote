// Shutter Remote: a remote for the phone's built-in camera.
// Middle button: take a photo (or start/stop a video). Up button: switch between "Instant" and "3 s timer".
// The Shutter Remote app on the phone receives the command and presses the button in the camera.
#include <pebble.h>

// Message keys between the watch and the phone (the same numbers are in ShutterListenerService.kt)
#define KEY_COMMAND 1   // watch -> phone: 1 = press the shutter
#define KEY_RESULT  2   // phone -> watch: result code

#define CMD_SHOOT 1

#define RES_OK          1
#define RES_NOT_CAMERA  2
#define RES_NO_BUTTON   3
#define RES_SERVICE_OFF 4

#define PERSIST_MODE 1
#define TIMER_SECONDS 3
#define REPLY_TIMEOUT_MS 4000
#define STATUS_SHOW_MS 3500

static Window *s_window;
static TextLayer *s_title;
static TextLayer *s_big;
static TextLayer *s_status;
static TextLayer *s_hint;

static int s_mode = 0;              // 0 = instant, 1 = 3 s timer
static int s_countdown = 0;         // > 0 while counting down
static bool s_waiting = false;      // waiting for the phone to answer
static AppTimer *s_tick_timer;
static AppTimer *s_reply_timer;
static AppTimer *s_status_timer;
static char s_big_buf[8];

static void show_mode(void) {
  text_layer_set_font(s_big, fonts_get_system_font(FONT_KEY_GOTHIC_28_BOLD));
  text_layer_set_text(s_big, s_mode == 0 ? "Instant" : "3 s timer");
}

static void clear_status_cb(void *data) {
  s_status_timer = NULL;
  text_layer_set_text(s_status, "");
}

static void set_status(const char *text, bool autoclear) {
  text_layer_set_text(s_status, text);
  if (s_status_timer) {
    app_timer_cancel(s_status_timer);
    s_status_timer = NULL;
  }
  if (autoclear) {
    s_status_timer = app_timer_register(STATUS_SHOW_MS, clear_status_cb, NULL);
  }
}

static void fail(const char *text) {
  s_waiting = false;
  if (s_reply_timer) {
    app_timer_cancel(s_reply_timer);
    s_reply_timer = NULL;
  }
  vibes_double_pulse();
  set_status(text, true);
}

static void reply_timeout_cb(void *data) {
  s_reply_timer = NULL;
  fail("The phone did not answer");
}

static void send_shoot(void) {
  DictionaryIterator *iter;
  AppMessageResult r = app_message_outbox_begin(&iter);
  if (r != APP_MSG_OK) {
    fail("No connection to the phone");
    return;
  }
  dict_write_uint8(iter, KEY_COMMAND, CMD_SHOOT);
  r = app_message_outbox_send();
  if (r != APP_MSG_OK) {
    fail("No connection to the phone");
    return;
  }
  s_waiting = true;
  set_status("…", false);
  if (s_reply_timer) app_timer_cancel(s_reply_timer);
  s_reply_timer = app_timer_register(REPLY_TIMEOUT_MS, reply_timeout_cb, NULL);
}

// Light vibration on every second of the countdown, shorter than the confirmation
static void tick_vibe(void) {
  static const uint32_t seg[] = { 40 };
  VibePattern pat = { .durations = seg, .num_segments = 1 };
  vibes_enqueue_custom_pattern(pat);
}

static void tick_cb(void *data) {
  s_tick_timer = NULL;
  s_countdown--;
  if (s_countdown <= 0) {
    show_mode();
    send_shoot();
    return;
  }
  snprintf(s_big_buf, sizeof(s_big_buf), "%d", s_countdown);
  text_layer_set_text(s_big, s_big_buf);
  tick_vibe();
  s_tick_timer = app_timer_register(1000, tick_cb, NULL);
}

static void cancel_countdown(void) {
  if (s_tick_timer) {
    app_timer_cancel(s_tick_timer);
    s_tick_timer = NULL;
  }
  s_countdown = 0;
  show_mode();
  set_status("Cancelled", true);
}

static void select_click(ClickRecognizerRef rec, void *ctx) {
  if (s_countdown > 0) {
    cancel_countdown();
    return;
  }
  if (s_waiting) return;  // the previous press is still on its way
  if (s_mode == 0) {
    send_shoot();
    return;
  }
  s_countdown = TIMER_SECONDS;
  text_layer_set_font(s_big, fonts_get_system_font(FONT_KEY_BITHAM_42_BOLD));
  snprintf(s_big_buf, sizeof(s_big_buf), "%d", s_countdown);
  text_layer_set_text(s_big, s_big_buf);
  set_status("Middle button cancels", false);
  tick_vibe();
  s_tick_timer = app_timer_register(1000, tick_cb, NULL);
}

static void up_click(ClickRecognizerRef rec, void *ctx) {
  if (s_countdown > 0) return;
  s_mode = s_mode == 0 ? 1 : 0;
  persist_write_int(PERSIST_MODE, s_mode);
  show_mode();
}

static void click_config(void *ctx) {
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click);
  window_single_click_subscribe(BUTTON_ID_UP, up_click);
}

static void inbox_received(DictionaryIterator *iter, void *ctx) {
  Tuple *t = dict_find(iter, KEY_RESULT);
  if (!t) return;
  int code;
  if (t->length == 1) code = t->value->uint8;
  else if (t->length == 2) code = t->value->uint16;
  else code = (int)t->value->int32;

  if (s_reply_timer) {
    app_timer_cancel(s_reply_timer);
    s_reply_timer = NULL;
  }
  s_waiting = false;

  switch (code) {
    case RES_OK:
      vibes_short_pulse();
      set_status("Done", true);
      break;
    case RES_NOT_CAMERA:
      fail("Open the camera on your phone");
      break;
    case RES_NO_BUTTON:
      fail("Set up the button in the phone app");
      break;
    case RES_SERVICE_OFF:
      fail("Turn on the service in the phone app");
      break;
    default:
      fail("Something went wrong");
      break;
  }
}

static void outbox_failed(DictionaryIterator *iter, AppMessageResult reason, void *ctx) {
  fail("No connection to the phone");
}

static TextLayer *make_layer(Layer *root, GRect r, const char *font, GTextAlignment align) {
  TextLayer *l = text_layer_create(r);
  text_layer_set_background_color(l, GColorClear);
  text_layer_set_text_color(l, GColorWhite);
  text_layer_set_font(l, fonts_get_system_font(font));
  text_layer_set_text_alignment(l, align);
  text_layer_set_overflow_mode(l, GTextOverflowModeWordWrap);
  layer_add_child(root, text_layer_get_layer(l));
  return l;
}

static void window_load(Window *w) {
  Layer *root = window_get_root_layer(w);
  GRect b = layer_get_bounds(root);
  int width = b.size.w;
  int h = b.size.h;

  window_set_background_color(w, GColorBlack);

  s_title = make_layer(root, GRect(0, 6, width, 30), FONT_KEY_GOTHIC_24_BOLD, GTextAlignmentCenter);
  text_layer_set_text_color(s_title, PBL_IF_COLOR_ELSE(GColorOrange, GColorWhite));
  text_layer_set_text(s_title, "Shutter Remote");

  s_big = make_layer(root, GRect(0, h / 2 - 40, width, 52), FONT_KEY_GOTHIC_28_BOLD, GTextAlignmentCenter);
  s_status = make_layer(root, GRect(8, h / 2 + 14, width - 16, 50), FONT_KEY_GOTHIC_18, GTextAlignmentCenter);

  s_hint = make_layer(root, GRect(4, h - 40, width - 8, 36), FONT_KEY_GOTHIC_14, GTextAlignmentCenter);
  text_layer_set_text_color(s_hint, PBL_IF_COLOR_ELSE(GColorLightGray, GColorWhite));
  text_layer_set_text(s_hint, "Up: mode\nMiddle: take photo");

  show_mode();
}

static void window_unload(Window *w) {
  text_layer_destroy(s_title);
  text_layer_destroy(s_big);
  text_layer_destroy(s_status);
  text_layer_destroy(s_hint);
}

int main(void) {
  if (persist_exists(PERSIST_MODE)) s_mode = persist_read_int(PERSIST_MODE) ? 1 : 0;

  app_message_register_inbox_received(inbox_received);
  app_message_register_outbox_failed(outbox_failed);
  app_message_open(64, 64);

  s_window = window_create();
  window_set_click_config_provider(s_window, click_config);
  window_set_window_handlers(s_window, (WindowHandlers) {
    .load = window_load,
    .unload = window_unload,
  });
  window_stack_push(s_window, true);

  app_event_loop();
  window_destroy(s_window);
}
