import {
  Bluetooth,
  Cable,
  ChevronDown,
  Gamepad2,
  GitFork,
  LayoutDashboard,
  Monitor,
  Network,
  SlidersHorizontal,
  Smartphone,
  Sparkles,
  Wifi,
  Zap,
} from 'lucide-react';
import Image from 'next/image';

const features = [
  {
    icon: Wifi,
    title: '局域网优先',
    description: '同一网络内快速发现电脑，减少等待与连接波动。',
  },
  {
    icon: Gamepad2,
    title: '原生手柄体验',
    description: '通过 Windows Companion 转换为 Xbox 360 手柄输入。',
  },
  {
    icon: LayoutDashboard,
    title: '布局自由调整',
    description: '按游戏习惯移动、缩放控件，保存自己的操作方式。',
  },
  {
    icon: Bluetooth,
    title: '多种连接方式',
    description: '局域网、蓝牙伴侣与蓝牙 HID 直连，按场景选择。',
  },
];

const connectionSteps = [
  {
    number: '01',
    icon: Monitor,
    title: '打开电脑伴侣',
    description: 'Windows Companion 会显示电脑名称和首次配对码。',
  },
  {
    number: '02',
    icon: Smartphone,
    title: '手机发现电脑',
    description: '在同一局域网中自动搜索，也可以手动输入 IP。',
  },
  {
    number: '03',
    icon: Zap,
    title: '连接，开始游戏',
    description: '输入配对码后，手机操作会映射为标准手柄输入。',
  },
];

export default function Home() {
  return (
    <main>
      <header className="site-header">
        <a className="brand" href="#top" aria-label="返回 MPad 首页">
          <Image
            src="/mpad-icon.png"
            alt=""
            width={512}
            height={512}
            priority
          />
          <span>MPad</span>
        </a>
        <nav aria-label="主导航">
          <a href="#features">功能</a>
          <a href="#connect">连接方式</a>
          <a href="#interface">产品界面</a>
        </nav>
        <a
          className="nav-action"
          href="https://github.com/rayqwe1234/MPad"
          target="_blank"
          rel="noreferrer"
        >
          <GitFork size={17} aria-hidden="true" />
          GitHub
        </a>
      </header>

      <section className="hero" id="top">
        <div className="hero-copy">
          <p className="eyebrow">
            <Sparkles size={15} aria-hidden="true" />
            手机模拟游戏手柄
          </p>
          <h1>
            <span className="headline-line">把手机，变成你的</span>
            <span className="headline-line headline-accent">
              Windows 手柄。
            </span>
          </h1>
          <p className="hero-intro">
            无需额外购买控制器。MPad 让 Android 手机通过局域网或蓝牙连接电脑，
            用熟悉的触控方式畅玩支持手柄的游戏。
          </p>
          <div className="hero-actions">
            <a className="button primary" href="#connect">
              看看如何连接
              <ChevronDown size={18} aria-hidden="true" />
            </a>
            <span className="availability">适用于 Android 与 Windows</span>
          </div>
          <dl className="hero-notes">
            <div>
              <dt>连接</dt>
              <dd>局域网 / 蓝牙</dd>
            </div>
            <div>
              <dt>输出</dt>
              <dd>XInput 手柄</dd>
            </div>
            <div>
              <dt>布局</dt>
              <dd>可自由编辑</dd>
            </div>
          </dl>
        </div>

        <div className="hero-visual" aria-label="MPad 手柄操作界面">
          <div className="signal signal-one" />
          <div className="signal signal-two" />
          <div className="product-frame">
            <div className="frame-bar">
              <span>
                <i /> 已连接到电脑
              </span>
              <span>MPad Controller</span>
            </div>
            <Image
              src="/screenshots/controller.jpg"
              alt="MPad 手机端虚拟手柄界面，包含摇杆、十字键、肩键与 ABXY 按钮"
              width={2400}
              height={1080}
              priority
            />
          </div>
          <div className="floating-note">
            <Network size={20} aria-hidden="true" />
            <span>
              <strong>局域网连接</strong>自动发现，快速配对
            </span>
          </div>
        </div>
      </section>

      <section className="feature-strip" id="features" aria-label="核心功能">
        {features.map(({ icon: Icon, title, description }) => (
          <article key={title}>
            <Icon aria-hidden="true" />
            <div>
              <h2>{title}</h2>
              <p>{description}</p>
            </div>
          </article>
        ))}
      </section>

      <section className="connection-section section-shell" id="connect">
        <div className="section-heading">
          <p className="kicker">简单连接</p>
          <h2>准备好，只要三步。</h2>
          <p>
            局域网模式把搜索、配对和输入传输串在一起。电脑端保持运行，手机端就能找到它。
          </p>
        </div>
        <div className="steps">
          {connectionSteps.map(({ number, icon: Icon, title, description }) => (
            <article key={number}>
              <div className="step-top">
                <span>{number}</span>
                <Icon aria-hidden="true" />
              </div>
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
        <div className="connection-flow" aria-label="连接流程">
          <span>
            <Smartphone aria-hidden="true" />
            Android 手机
          </span>
          <i />
          <span>
            <Wifi aria-hidden="true" />
            同一局域网
          </span>
          <i />
          <span>
            <Monitor aria-hidden="true" />
            Windows 电脑
          </span>
        </div>
      </section>

      <section className="showcase section-shell" id="interface">
        <div className="section-heading showcase-heading">
          <div>
            <p className="kicker">真实界面</p>
            <h2>连接状态，一眼看清。</h2>
          </div>
          <p>
            手机端负责发现设备与操控，Windows Companion
            负责连接、驱动状态和实时输入。
          </p>
        </div>
        <div className="showcase-grid">
          <figure className="screen-card screen-card-wide">
            <div className="screen-media">
              <Image
                src="/screenshots/connection.jpg"
                alt="MPad 手机端局域网电脑发现与连接界面"
                width={2400}
                height={1080}
              />
            </div>
            <figcaption>
              <span>Android</span>
              <div>
                <h3>发现与配对</h3>
                <p>自动列出局域网中的电脑，也保留手动 IP 连接方式。</p>
              </div>
            </figcaption>
          </figure>
          <figure className="screen-card screen-card-tall">
            <div className="screen-media companion-media">
              <Image
                src="/screenshots/companion.png"
                alt="MPad Windows Companion 配对码、连接状态与实时输入界面"
                width={1122}
                height={1165}
              />
            </div>
            <figcaption>
              <span>Windows</span>
              <div>
                <h3>电脑伴侣</h3>
                <p>配对码、驱动、连接状态和当前输入都集中显示。</p>
              </div>
            </figcaption>
          </figure>
        </div>
      </section>

      <section className="control-section section-shell">
        <div className="control-copy">
          <p className="kicker">为触控而设计</p>
          <h2>不是把按钮搬到屏幕上，而是让操作真正顺手。</h2>
          <p>
            摇杆会跟随手指持续移动，按钮按实际形状触发。横屏布局让重要控件落在拇指自然覆盖的位置。
          </p>
          <ul>
            <li>
              <SlidersHorizontal aria-hidden="true" />
              拖动与缩放每个控件
            </li>
            <li>
              <Gamepad2 aria-hidden="true" />
              摇杆、十字键、扳机键与 ABXY 完整布局
            </li>
            <li>
              <Cable aria-hidden="true" />
              按网络环境切换连接方案
            </li>
          </ul>
        </div>
        <div className="control-image">
          <Image
            src="/screenshots/controller.jpg"
            alt="MPad 横屏虚拟手柄完整布局"
            width={2400}
            height={1080}
          />
          <div className="crop-label">可编辑触控布局</div>
        </div>
      </section>

      <section className="requirements section-shell">
        <div>
          <p className="kicker">开始之前</p>
          <h2>一部手机，一台电脑。</h2>
        </div>
        <dl>
          <div>
            <dt>手机端</dt>
            <dd>Android 设备，横屏使用</dd>
          </div>
          <div>
            <dt>电脑端</dt>
            <dd>Windows 10 / 11 与 MPad Companion</dd>
          </div>
          <div>
            <dt>局域网模式</dt>
            <dd>手机与电脑连接同一网络</dd>
          </div>
        </dl>
      </section>

      <section className="closing section-shell">
        <Image
          src="/mpad-icon.png"
          alt="MPad 应用图标"
          width={512}
          height={512}
        />
        <p className="kicker">MPad</p>
        <h2>下一局，用你的手机来控制。</h2>
        <p className="closing-copy">
          查看源代码、版本说明，并从 GitHub 获取最新正式版。
        </p>
        <a
          className="github-button"
          href="https://github.com/rayqwe1234/MPad"
          target="_blank"
          rel="noreferrer"
        >
          <GitFork size={19} aria-hidden="true" />
          github.com/rayqwe1234/MPad
        </a>
      </section>

      <footer>
        <a className="brand" href="#top">
          <Image src="/mpad-icon.png" alt="" width={512} height={512} />
          <span>MPad</span>
        </a>
        <p>手机模拟游戏手柄 · Android + Windows</p>
        <a
          className="footer-github"
          href="https://github.com/rayqwe1234/MPad"
          target="_blank"
          rel="noreferrer"
        >
          <GitFork size={15} aria-hidden="true" />
          GitHub
        </a>
      </footer>
    </main>
  );
}
