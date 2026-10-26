import { ReloadOutlined, LoadingOutlined } from '@ant-design/icons';

type Props = {
  image?: string;
  loading?: boolean;
  failed?: boolean;
  label?: string;
  onRefresh: () => void;
  onError: () => void;
};

export default function CaptchaImage({ image, loading, failed, label = '图形验证码', onRefresh, onError }: Props) {
  return (
    <button type="button" className="public-service-captcha" onClick={onRefresh}
      aria-label={`刷新${label}`} aria-busy={Boolean(loading)} disabled={loading} title="点击换一张验证码">
      {loading ? <span><LoadingOutlined /> 加载中</span>
        : image && !failed ? <img src={image} alt={`${label}图片`} onError={onError} draggable={false} />
        : <span><ReloadOutlined /> {failed ? '加载失败，重试' : '点击获取验证码'}</span>}
    </button>
  );
}
