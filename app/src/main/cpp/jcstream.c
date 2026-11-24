// jcstream: reads both Switch Joy-Cons (hid-nintendo evdev nodes) on the Aura puck
// and streams their state over UDP to the PC, bypassing Android input and Virtual Desktop.
// Joy-Con 2 have no kernel driver: our fork of Joycon2Android talks to them over Bluetooth LE and feeds their
// reports in on 127.0.0.1.
// Runs as UID shell (adb shell or Shizuku) for Joy-Con 1; Joy-Con 2 alone need no special user.
// See PROTOCOL.md in koshisan/joyhands for the packet layout. Joycon2Android (koshisan/joycon2android) carries a
// copy of this file as app/src/main/cpp/jcstream.c and starts it; change this one and copy it over.
// Without an address it finds the PC by broadcast and follows it; the PC's status packets tell it the link is alive.
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <ifaddrs.h>
#include <linux/input.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <net/if.h>
#include <netinet/in.h>
#include <sys/ioctl.h>
#include <sys/socket.h>

#define JH_MAGIC 0x3130484Au  // "JH01" little-endian
#define JH_HELLO 0x3151484Au  // "JHQ1"
#define JH_STATUS 0x3153484Au // "JHS1"
#define JH_INFO 0x3149484Au   // "JHI1"
#define JH_LOCAL 0x314C484Au  // "JHL1"
#define JH_BLE 0x3142484Au    // "JHB1"
#define JH_HAPTIC 0x3156484Au // "JHV1"
#define JH_PROTO 1
#define JH_PORT 27815
#define JH_LOCAL_PORT 27816 // on 127.0.0.1: everything in one packet for the app on the puck
#define JH_BLE_PORT 27817   // on 127.0.0.1: Joycon2Android's reports of Joy-Con 2
#define JH_HAPTIC_PORT 27818 // on 127.0.0.1: vibration for Joy-Con 2, taken by Joycon2Android
#define PC_TIMEOUT_US 3000000
#define BLE_TIMEOUT_US 500000 // a Joy-Con 2 reports every 15 to 30 ms
#define C_BLE 16            // connected: this side is a Joy-Con 2 fed in by Joycon2Android

enum { LEFT = 0, RIGHT = 1 };
enum { PAD = 0, IMU = 1 };

// Button bits, identical on PC side.
enum {
    B_SHOULDER = 1 << 0, // L / R
    B_TRIGGER = 1 << 1,  // ZL / ZR
    B_SL = 1 << 2,
    B_SR = 1 << 3,
    B_MENU = 1 << 4,   // minus / plus
    B_STICK = 1 << 5,  // stick click
    B_SYSTEM = 1 << 6, // capture / home
    B_SOUTH = 1 << 7,  // dpad down / B
    B_EAST = 1 << 8,   // dpad right / A
    B_WEST = 1 << 9,   // dpad left / Y
    B_NORTH = 1 << 10, // dpad up / X
    B_CHAT = 1 << 11,  // C on the right Joy-Con 2
};

#pragma pack(push, 1)
struct jh_side {
    uint8_t connected; // bit0 pad node open, bit1 imu node open, bit2 grabbed, bit3 held in a hand, bit4 Joy-Con 2
    uint16_t buttons;
    int16_t stick_x; // right = +, raw kernel calibration, -32767..32767
    int16_t stick_y; // kernel sign (down = +)
    int16_t accel[3]; // 4096 per g
    int32_t gyro[3];  // 14247 per deg/s (kernel resolution), milli-units
    uint32_t imu_us;  // MSC_TIMESTAMP of the last IMU sample
};
struct jh_packet {
    uint32_t magic;
    uint32_t seq;
    uint64_t t_us; // CLOCK_MONOTONIC on the puck
    struct jh_side side[2];
};
struct jh_hello { // puck -> broadcast while searching
    uint32_t magic;
    uint8_t proto, pad[3];
};
struct jh_status { // PC -> puck: answer to a hello, and once per second while the stream arrives
    uint32_t magic;
    uint8_t proto;
    uint8_t flags;    // bit0 left hand device registered in SteamVR, bit1 right
    uint16_t rate;    // state packets the PC received in the last second
    char version[16]; // driver version
    char host[32];    // computer name
};
struct jh_extra { // what only a Joy-Con 2 tells about itself
    uint8_t battery;   // percent, 255 = unknown
    uint8_t has_color; // 1 if color is set
    uint8_t color[3];  // accent colour of the shell: red, green, blue
};
struct jh_info_side {
    uint8_t mac[6];
    uint16_t reports;    // evdev reports of this Joy-Con in the last second
    uint16_t max_gap_ms; // longest pause between two of them, 65535 = none arrived
};
struct jh_info { // puck -> PC, once per second
    uint32_t magic;
    uint8_t proto;
    uint8_t state; // 0 searching, 1 PC answers
    uint16_t pad;
    struct jh_info_side side[2];
    struct jh_extra extra[2];
};
struct jh_haptic { // PC -> puck for every vibration SteamVR asks for; passed on to 127.0.0.1:27818 unchanged
    uint32_t magic;
    uint8_t proto;
    uint8_t side;
    uint16_t duration_ms; // 0 = one short pulse
    uint16_t frequency;   // Hz
    uint16_t amplitude;   // 0..65535
    uint16_t pad;
};
struct jh_local { // puck -> 127.0.0.1:27816, on changes (at most 50 per second) and at least 5 per second
    uint32_t magic;
    uint8_t proto;
    uint8_t flags; // bit0 PC answers, bit1 started with a fixed address
    uint16_t pad;
    uint32_t pc_ip; // network byte order, 0 while searching
    uint8_t pc_flags, pad2;
    uint16_t pc_rate;
    char pc_version[16];
    char pc_host[32];
    struct jh_side side[2];
    struct jh_info_side info[2];
    struct jh_extra extra[2];
};
struct jh_ble { // Joycon2Android -> 127.0.0.1:27817, one per report of a Joy-Con 2
    uint32_t magic;
    uint8_t proto;
    uint8_t side;
    uint8_t mac[6];
    uint16_t buttons;
    int16_t stick_x, stick_y; // as in jh_side
    int16_t accel[3];
    int32_t gyro[3];
    struct jh_extra extra;
};
#pragma pack(pop)

struct node {
    int fd;
    char path[64];
};

static struct node nodes[2][2];
static struct jh_packet pkt;
static struct jh_info info;
// report statistics of the running second, per side
static uint64_t last_report[2];
static uint32_t cur_reports[2], cur_gap_us[2];
// "Held in a hand" comes from the gyroscope: a Joy-Con that lies somewhere does not rotate, one in a hand always
// does a little. Per side, the spread of every axis is taken over windows of MOTION_WINDOW_US; a rotation spread
// above MOTION_GYRO, or a button or stick, counts as motion, and the Joy-Con counts as held until HELD_TIMEOUT_US
// after the last motion. Measured: lying on a table under 5 deg/s, up to 12 when the table is bumped, and 30 to 100
// when only its strap is touched. The acceleration is no help: a bump on the table shows there ten times as large
// as at rest (100 against 10 counts) and would count as a hand.
#define MOTION_WINDOW_US 500000
#define HELD_TIMEOUT_US 3000000
#define MOTION_GYRO 15000  // thousandths of a degree per second
#define MOTION_STICK 3000
struct motion {
    int32_t lo[6], hi[6]; // accel x y z, gyro x y z in the running window
    int samples;
    int skip; // windows still to ignore after the sensor appeared: its first reports carry only part of the axes
    uint64_t window_start, last_motion;
    int16_t stick_x, stick_y; // where the stick was when it last counted as motion
    int32_t accel_spread, gyro_spread; // of the last complete window, for --dump
};
static struct motion motion[2];
static uint16_t seen_buttons[2];
static uint64_t ble_last[2]; // when this side was last fed
static struct jh_extra extra[2] = {{.battery = 255}, {.battery = 255}};
static int opt_grab = 1, opt_dump = 0;
static volatile sig_atomic_t stop;

static void on_sig(int s) { (void)s; stop = 1; }

static uint64_t now_us(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000u + ts.tv_nsec / 1000;
}

// A report with fresh motion values arrived at time r.
static void note_imu(int side, uint64_t r) {
    struct motion *mo = &motion[side];
    struct jh_side *js = &pkt.side[side];
    for (int k = 0; k < 6; k++) {
        int32_t v = k < 3 ? js->accel[k] : js->gyro[k - 3];
        if (!mo->samples || v < mo->lo[k]) mo->lo[k] = v;
        if (!mo->samples || v > mo->hi[k]) mo->hi[k] = v;
    }
    mo->samples++;
    if (r - mo->window_start >= MOTION_WINDOW_US) {
        mo->accel_spread = mo->gyro_spread = 0;
        for (int k = 0; k < 6; k++) {
            int32_t d = mo->hi[k] - mo->lo[k];
            if (k < 3 && d > mo->accel_spread) mo->accel_spread = d;
            if (k >= 3 && d > mo->gyro_spread) mo->gyro_spread = d;
        }
        if (mo->skip > 0) mo->skip--;
        else if (mo->gyro_spread > MOTION_GYRO) mo->last_motion = r;
        mo->samples = 0;
        mo->window_start = r;
    }
}

// A report with buttons and stick arrived at time r.
static void note_pad(int side, uint64_t r) {
    struct motion *mo = &motion[side];
    struct jh_side *js = &pkt.side[side];
    if (js->buttons != seen_buttons[side] || abs(js->stick_x - mo->stick_x) > MOTION_STICK ||
        abs(js->stick_y - mo->stick_y) > MOTION_STICK) {
        mo->last_motion = r;
        mo->stick_x = js->stick_x;
        mo->stick_y = js->stick_y;
        seen_buttons[side] = js->buttons;
    }
}

static void note_reports(int side, uint64_t r, int n) {
    if (last_report[side] && r - last_report[side] > cur_gap_us[side]) cur_gap_us[side] = (uint32_t)(r - last_report[side]);
    last_report[side] = r;
    cur_reports[side] += n;
}

// Joycon2Android's report of a Joy-Con 2. A Joy-Con 1 on the same side wins: the kernel has it, so it is really there.
static int apply_ble(const struct jh_ble *b, uint64_t r) {
    if (b->side > 1 || nodes[b->side][PAD].fd >= 0 || nodes[b->side][IMU].fd >= 0) return 0;
    int side = b->side;
    struct jh_side *s = &pkt.side[side];
    if (!(s->connected & C_BLE)) {
        fprintf(stderr, "Joy-Con 2 %s arrives\n", side ? "right" : "left");
        memset(&motion[side], 0, sizeof motion[side]);
        s->connected = 1 | 2 | 4 | C_BLE;
    }
    memcpy(info.side[side].mac, b->mac, 6);
    extra[side] = b->extra;
    s->buttons = b->buttons;
    s->stick_x = b->stick_x;
    s->stick_y = b->stick_y;
    memcpy(s->accel, b->accel, sizeof s->accel);
    memcpy(s->gyro, b->gyro, sizeof s->gyro);
    s->imu_us = (uint32_t)r;
    ble_last[side] = r;
    note_imu(side, r);
    note_pad(side, r);
    note_reports(side, r, 1);
    return 1;
}

static void drop_ble(int side) {
    struct jh_side *s = &pkt.side[side];
    if (!(s->connected & C_BLE)) return;
    fprintf(stderr, "Joy-Con 2 %s is gone\n", side ? "right" : "left");
    memset(s, 0, sizeof *s);
    memset(&extra[side], 0, sizeof extra[side]);
    extra[side].battery = 255;
}

static uint16_t map_button(int side, int code) {
    if (side == LEFT) {
        switch (code) {
        case BTN_TL: return B_SHOULDER;
        case BTN_TL2: return B_TRIGGER;
        case BTN_TR: return B_SL;
        case BTN_TR2: return B_SR;
        case BTN_SELECT: return B_MENU;
        case BTN_THUMBL: return B_STICK;
        case BTN_Z: return B_SYSTEM;
        case BTN_DPAD_DOWN: return B_SOUTH;
        case BTN_DPAD_RIGHT: return B_EAST;
        case BTN_DPAD_LEFT: return B_WEST;
        case BTN_DPAD_UP: return B_NORTH;
        }
    } else {
        switch (code) {
        case BTN_TR: return B_SHOULDER;
        case BTN_TR2: return B_TRIGGER;
        case BTN_TL: return B_SL;
        case BTN_TL2: return B_SR;
        case BTN_START: return B_MENU;
        case BTN_THUMBR: return B_STICK;
        case BTN_MODE: return B_SYSTEM;
        case BTN_SOUTH: return B_SOUTH;
        case BTN_EAST: return B_EAST;
        case BTN_WEST: return B_WEST;
        case BTN_NORTH: return B_NORTH;
        }
    }
    return 0;
}

static void close_node(int side, int kind) {
    struct node *n = &nodes[side][kind];
    if (n->fd >= 0) {
        fprintf(stderr, "lost %s\n", n->path);
        close(n->fd);
    }
    n->fd = -1;
    struct jh_side *s = &pkt.side[side];
    if (kind == PAD) {
        s->connected &= ~(1 | 4);
        s->buttons = 0;
        s->stick_x = s->stick_y = 0;
    } else {
        s->connected &= ~2;
    }
}

// Opens Joy-Con nodes that are not open yet. Called at start and periodically.
static void scan(void) {
    DIR *d = opendir("/dev/input");
    if (!d) return;
    struct dirent *e;
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, "event", 5)) continue;
        char path[64], name[128] = "";
        snprintf(path, sizeof path, "/dev/input/%s", e->d_name);
        int known = 0;
        for (int i = 0; i < 4; i++)
            if (nodes[i / 2][i % 2].fd >= 0 && !strcmp(nodes[i / 2][i % 2].path, path)) known = 1;
        if (known) continue;
        int fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) continue;
        ioctl(fd, EVIOCGNAME(sizeof name - 1), name);
        int side = strstr(name, "Left Joy-Con") ? LEFT : strstr(name, "Right Joy-Con") ? RIGHT : -1;
        if (side < 0 || strncmp(name, "Nintendo Switch", 15) || nodes[side][strstr(name, "IMU") ? IMU : PAD].fd >= 0) {
            close(fd);
            continue;
        }
        int kind = strstr(name, "IMU") ? IMU : PAD;
        struct jh_side *s = &pkt.side[side];
        drop_ble(side);
        if (kind == PAD) {
            s->connected |= 1;
            // the kernel driver reports the Bluetooth address as the device's unique id
            char uniq[64] = "";
            unsigned m[6];
            if (ioctl(fd, EVIOCGUNIQ(sizeof uniq - 1), uniq) >= 0 &&
                sscanf(uniq, "%x:%x:%x:%x:%x:%x", &m[0], &m[1], &m[2], &m[3], &m[4], &m[5]) == 6)
                for (int k = 0; k < 6; k++) info.side[side].mac[k] = (uint8_t)m[k];
            // evdev only reports changes, so fetch the resting stick position once.
            struct input_absinfo ai;
            if (ioctl(fd, EVIOCGABS(side == LEFT ? ABS_X : ABS_RX), &ai) == 0) s->stick_x = (int16_t)ai.value;
            if (ioctl(fd, EVIOCGABS(side == LEFT ? ABS_Y : ABS_RY), &ai) == 0) s->stick_y = (int16_t)ai.value;
            if (opt_grab) {
                if (ioctl(fd, EVIOCGRAB, 1) == 0) s->connected |= 4;
                else fprintf(stderr, "EVIOCGRAB %s failed: %s\n", path, strerror(errno));
            }
        } else {
            s->connected |= 2;
            memset(&motion[side], 0, sizeof motion[side]);
            motion[side].skip = 2;
        }
        nodes[side][kind].fd = fd;
        snprintf(nodes[side][kind].path, sizeof nodes[side][kind].path, "%s", path);
        fprintf(stderr, "opened %s \"%s\"%s\n", path, name, (kind == PAD && (s->connected & 4)) ? " (grabbed)" : "");
    }
    closedir(d);
}

// Returns 1 when the event completes a report.
static int apply(int side, int kind, const struct input_event *ev) {
    struct jh_side *s = &pkt.side[side];
    if (kind == PAD) {
        if (ev->type == EV_KEY) {
            uint16_t bit = map_button(side, ev->code);
            if (!bit) {
                if (opt_dump) fprintf(stderr, "unmapped key side=%d code=0x%x value=%d\n", side, ev->code, ev->value);
                return 0;
            }
            if (ev->value) s->buttons |= bit;
            else s->buttons &= ~bit;
        } else if (ev->type == EV_ABS) {
            if (ev->code == ABS_X || ev->code == ABS_RX) s->stick_x = (int16_t)ev->value;
            else if (ev->code == ABS_Y || ev->code == ABS_RY) s->stick_y = (int16_t)ev->value;
        } else if (ev->type == EV_SYN && ev->code == SYN_REPORT) {
            return 1;
        }
    } else {
        if (ev->type == EV_ABS) {
            if (ev->code <= ABS_Z) s->accel[ev->code - ABS_X] = (int16_t)ev->value;
            else if (ev->code >= ABS_RX && ev->code <= ABS_RZ) s->gyro[ev->code - ABS_RX] = ev->value;
        } else if (ev->type == EV_MSC && ev->code == MSC_TIMESTAMP) {
            s->imu_us = (uint32_t)ev->value;
        } else if (ev->type == EV_SYN && ev->code == SYN_REPORT) {
            return 1;
        }
    }
    return 0;
}

// Sends the hello to the broadcast address of every network interface that has one.
static void send_hello(int sock, int port) {
    struct jh_hello h = {.magic = JH_HELLO, .proto = JH_PROTO};
    struct ifaddrs *list = NULL;
    int sent = 0;
    if (getifaddrs(&list) == 0) {
        for (struct ifaddrs *a = list; a; a = a->ifa_next) {
            if (!a->ifa_addr || a->ifa_addr->sa_family != AF_INET || !a->ifa_broadaddr) continue;
            if (!(a->ifa_flags & IFF_UP) || !(a->ifa_flags & IFF_BROADCAST) || (a->ifa_flags & IFF_LOOPBACK)) continue;
            struct sockaddr_in to = *(struct sockaddr_in *)a->ifa_broadaddr;
            to.sin_port = htons(port);
            if (sendto(sock, &h, sizeof h, 0, (struct sockaddr *)&to, sizeof to) == sizeof h) sent++;
        }
        freeifaddrs(list);
    }
    if (!sent) {
        struct sockaddr_in to = {.sin_family = AF_INET, .sin_port = htons(port)};
        to.sin_addr.s_addr = htonl(INADDR_BROADCAST);
        sendto(sock, &h, sizeof h, 0, (struct sockaddr *)&to, sizeof to);
    }
}

int main(int argc, char **argv) {
    const char *host = NULL;
    int port = JH_PORT;
    for (int i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--nograb")) opt_grab = 0;
        else if (!strcmp(argv[i], "--dump")) opt_dump = 1;
        else if (!strcmp(argv[i], "--port") && i + 1 < argc) port = atoi(argv[++i]);
        else if (argv[i][0] == '-') {
            fprintf(stderr, "usage: jcstream [pc-ip] [--port n] [--nograb] [--dump]\n"
                            "without pc-ip the PC is found by broadcast\n");
            return 2;
        } else if (!host) host = argv[i];
        else port = atoi(argv[i]);
    }
    // dst is where the state goes; have_dst is 0 while searching. A given address is never dropped.
    struct sockaddr_in dst = {.sin_family = AF_INET, .sin_port = htons(port)};
    int have_dst = 0, fixed = host != NULL;
    if (host) {
        if (inet_pton(AF_INET, host, &dst.sin_addr) != 1) {
            fprintf(stderr, "bad address %s\n", host);
            return 2;
        }
        have_dst = 1;
    }
    int sock = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    if (sock < 0) {
        perror("socket");
        return 1;
    }
    int one = 1;
    setsockopt(sock, SOL_SOCKET, SO_BROADCAST, &one, sizeof one);
    signal(SIGINT, on_sig);
    signal(SIGTERM, on_sig);
    signal(SIGHUP, on_sig);
    for (int i = 0; i < 4; i++) nodes[i / 2][i % 2].fd = -1;
    // Joycon2Android's Joy-Con 2 reports. The port also tells whether a sender is running already: two would both
    // stream to the PC, and only one would get the Joy-Con 2.
    int feed = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    struct sockaddr_in feed_addr = {.sin_family = AF_INET, .sin_port = htons(JH_BLE_PORT)};
    feed_addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    int bound = feed >= 0 ? bind(feed, (struct sockaddr *)&feed_addr, sizeof feed_addr) : -1;
    if (bound < 0 && errno == EADDRINUSE) {
        fprintf(stderr, "another jcstream is running\n");
        return 3;
    }
    if (bound < 0) {
        fprintf(stderr, "no port %d for Joy-Con 2: %s\n", JH_BLE_PORT, strerror(errno));
        if (feed >= 0) close(feed);
        feed = -1;
    }
    pkt.magic = JH_MAGIC;
    info.magic = JH_INFO;
    info.proto = JH_PROTO;
    info.side[0].max_gap_ms = info.side[1].max_gap_ms = 65535;
    if (!fixed) fprintf(stderr, "searching for the PC on UDP port %d\n", port);

    uint64_t last_scan = 0, last_send = 0, last_dump = 0, last_hello = 0, last_status = 0, last_second = now_us();
    uint64_t last_local = 0;
    struct sockaddr_in local = {.sin_family = AF_INET, .sin_port = htons(JH_LOCAL_PORT)};
    local.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    struct sockaddr_in haptic_to = {.sin_family = AF_INET, .sin_port = htons(JH_HAPTIC_PORT)};
    haptic_to.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    struct jh_local lp, lp_sent;
    memset(&lp_sent, 0, sizeof lp_sent);
    uint32_t sent = 0;
    struct jh_status pc;
    memset(&pc, 0, sizeof pc);
    while (!stop) {
        uint64_t t = now_us();
        if (t - last_scan > 1000000 || !last_scan) {
            scan();
            last_scan = t;
        }
        struct pollfd pfd[6];
        for (int i = 0; i < 4; i++) {
            pfd[i].fd = nodes[i / 2][i % 2].fd;
            pfd[i].events = POLLIN;
            pfd[i].revents = 0;
        }
        pfd[4].fd = sock;
        pfd[4].events = POLLIN;
        pfd[4].revents = 0;
        pfd[5].fd = feed;
        pfd[5].events = POLLIN;
        pfd[5].revents = 0;
        int dirty = 0;
        if (poll(pfd, 6, 20) > 0) {
            for (int i = 0; i < 4; i++) {
                if (pfd[i].fd < 0 || !pfd[i].revents) continue;
                struct input_event ev[64];
                ssize_t n = read(pfd[i].fd, ev, sizeof ev);
                if (n < 0 && (errno == EAGAIN || errno == EINTR)) continue;
                if (n <= 0) { // ENODEV: Joy-Con disconnected
                    close_node(i / 2, i % 2);
                    dirty = 1;
                    continue;
                }
                int side = i / 2, reports = 0;
                for (size_t k = 0; k < (size_t)n / sizeof ev[0]; k++) reports += apply(side, i % 2, &ev[k]);
                if (reports) {
                    uint64_t r = now_us();
                    if (i % 2 == IMU) note_imu(side, r);
                    else note_pad(side, r);
                    note_reports(side, r, reports);
                    dirty = 1;
                }
            }
            while (feed >= 0) {
                struct jh_ble b;
                ssize_t n = recv(feed, &b, sizeof b, 0);
                if (n < 0) break;
                if (n == sizeof b && b.magic == JH_BLE && b.proto == JH_PROTO) dirty |= apply_ble(&b, now_us());
            }
            // status and vibration packets from the PC
            for (;;) {
                union {
                    struct jh_status st;
                    struct jh_haptic haptic;
                } in;
                struct sockaddr_in from;
                socklen_t fl = sizeof from;
                ssize_t n = recvfrom(sock, &in, sizeof in, 0, (struct sockaddr *)&from, &fl);
                if (n < 0) break;
                if (n == sizeof in.haptic && in.haptic.magic == JH_HAPTIC) {
                    // Only a Joy-Con 2 can be reached: Joycon2Android holds its connection. A Joy-Con 1 would take
                    // it through the kernel's force feedback, which is not built.
                    int side = in.haptic.side;
                    if (have_dst && from.sin_addr.s_addr == dst.sin_addr.s_addr && side < 2 &&
                        (pkt.side[side].connected & C_BLE))
                        sendto(sock, &in.haptic, sizeof in.haptic, 0, (struct sockaddr *)&haptic_to, sizeof haptic_to);
                    continue;
                }
                struct jh_status st = in.st;
                if (n != sizeof st || st.magic != JH_STATUS) continue;
                if (have_dst && from.sin_addr.s_addr != dst.sin_addr.s_addr) continue; // some other PC
                st.version[sizeof st.version - 1] = st.host[sizeof st.host - 1] = 0;
                if (!info.state) {
                    char ip[32] = "";
                    inet_ntop(AF_INET, &from.sin_addr, ip, sizeof ip);
                    fprintf(stderr, "PC %s (%s) answers, driver %s\n", st.host, ip, st.version);
                }
                dst.sin_addr = from.sin_addr;
                have_dst = 1;
                info.state = 1;
                last_status = now_us();
                pc = st;
            }
        }
        t = now_us();
        for (int s = 0; s < 2; s++) {
            struct jh_side *js = &pkt.side[s];
            if ((js->connected & C_BLE) && t - ble_last[s] > BLE_TIMEOUT_US) {
                drop_ble(s);
                dirty = 1;
            }
            // without the motion sensor there is nothing to tell by: a connected Joy-Con then counts as held
            int held = (js->connected & 1) && (!(js->connected & 2) ||
                                               (motion[s].last_motion && t - motion[s].last_motion < HELD_TIMEOUT_US));
            if (held != !!(js->connected & 8)) {
                js->connected ^= 8;
                dirty = 1;
            }
        }
        if (info.state && t - last_status > PC_TIMEOUT_US) {
            fprintf(stderr, "PC stopped answering%s\n", fixed ? "" : ", searching again");
            info.state = 0;
            memset(&pc, 0, sizeof pc);
            if (!fixed) have_dst = 0;
        }
        if (!have_dst && (t - last_hello >= 1000000 || !last_hello)) {
            send_hello(sock, port);
            last_hello = t;
        }
        // Send on every completed report, and at 50 Hz as keepalive so the PC notices loss.
        if (have_dst && (dirty || t - last_send >= 20000)) {
            pkt.seq++;
            pkt.t_us = t;
            sendto(sock, &pkt, sizeof pkt, 0, (struct sockaddr *)&dst, sizeof dst);
            last_send = t;
            sent++;
        }
        if (t - last_second >= 1000000) {
            last_second = t;
            for (int s = 0; s < 2; s++) {
                // a pause that is still running counts too
                if (last_report[s] && t - last_report[s] > cur_gap_us[s]) cur_gap_us[s] = (uint32_t)(t - last_report[s]);
                uint32_t ms = cur_gap_us[s] / 1000;
                int none = !last_report[s] || !(pkt.side[s].connected & 3) || ms > 65535;
                info.side[s].reports = cur_reports[s] > 65535 ? 65535 : (uint16_t)cur_reports[s];
                info.side[s].max_gap_ms = none ? 65535 : (uint16_t)ms;
                info.extra[s] = extra[s];
                cur_reports[s] = cur_gap_us[s] = 0;
            }
            if (have_dst) sendto(sock, &info, sizeof info, 0, (struct sockaddr *)&dst, sizeof dst);
        }
        // the app on the puck: buttons and sticks at once, the rest whenever it changes
        memset(&lp, 0, sizeof lp);
        lp.magic = JH_LOCAL;
        lp.proto = JH_PROTO;
        lp.flags = (info.state ? 1 : 0) | (fixed ? 2 : 0);
        lp.pc_ip = have_dst ? dst.sin_addr.s_addr : 0;
        lp.pc_flags = pc.flags;
        lp.pc_rate = pc.rate;
        memcpy(lp.pc_version, pc.version, sizeof lp.pc_version);
        memcpy(lp.pc_host, pc.host, sizeof lp.pc_host);
        for (int s = 0; s < 2; s++) {
            lp.side[s] = pkt.side[s];
            // the IMU values change with every report and are of no use to the app
            memset(lp.side[s].accel, 0, sizeof lp.side[s].accel);
            memset(lp.side[s].gyro, 0, sizeof lp.side[s].gyro);
            lp.side[s].imu_us = 0;
            lp.info[s] = info.side[s];
            lp.extra[s] = extra[s];
        }
        if ((memcmp(&lp, &lp_sent, sizeof lp) && t - last_local >= 20000) || t - last_local >= 200000) {
            sendto(sock, &lp, sizeof lp, 0, (struct sockaddr *)&local, sizeof local);
            lp_sent = lp;
            last_local = t;
        }
        if (opt_dump && t - last_dump > 200000) {
            last_dump = t;
            for (int s = 0; s < 2; s++)
                fprintf(stderr, "%c c=%2d %s b=%04x st=%6d,%6d acc~%4d gyro~%3d rep=%3u gap=%5ums  ", s ? 'R' : 'L',
                        pkt.side[s].connected, pkt.side[s].connected & 8 ? "held" : "idle", pkt.side[s].buttons,
                        pkt.side[s].stick_x, pkt.side[s].stick_y, motion[s].accel_spread, motion[s].gyro_spread / 1000,
                        info.side[s].reports, info.side[s].max_gap_ms);
            if (info.state)
                fprintf(stderr, "pc=%s hands=%c%c rx=%u/s ", pc.host, pc.flags & 1 ? 'L' : '-', pc.flags & 2 ? 'R' : '-',
                        pc.rate);
            else fprintf(stderr, "pc=%s ", have_dst ? "silent" : "searching");
            fprintf(stderr, "pps=%u\n", sent * 5);
            sent = 0;
        }
    }
    // Closing the fds releases the grabs; Android sees the Joy-Cons again.
    for (int i = 0; i < 4; i++)
        if (nodes[i / 2][i % 2].fd >= 0) close(nodes[i / 2][i % 2].fd);
    return 0;
}
