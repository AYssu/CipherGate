import React, { useEffect, useRef, useCallback, useState } from 'react';
import { createPortal } from 'react-dom';

interface M5BottomSheetProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  closeText?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  maxHeight?: string;
}

const M5BottomSheet: React.FC<M5BottomSheetProps> = ({
  open,
  onClose,
  title,
  closeText,
  children,
  footer,
  maxHeight = '85vh',
}) => {
  const sheetRef = useRef<HTMLDivElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const startY = useRef(0);
  const currentY = useRef(0);
  const isDragging = useRef(false);

  // 简化状态：mounted 控制是否渲染，shouldAnimate 控制动画方向
  const [mounted, setMounted] = useState(false);
  const [shouldAnimate, setShouldAnimate] = useState(false);

  const openedAtRef = useRef(0);
  const generationRef = useRef(0);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const animateTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  // open 变化时控制挂载和动画
  useEffect(() => {
    if (open) {
      // 新一次打开：取消可能还在跑的关闭定时器
      generationRef.current++;
      if (closeTimerRef.current) {
        clearTimeout(closeTimerRef.current);
        closeTimerRef.current = null;
      }
      if (animateTimerRef.current) {
        clearTimeout(animateTimerRef.current);
        animateTimerRef.current = null;
      }
      openedAtRef.current = Date.now();
      setMounted(true);
      // 下一帧触发动画：先渲染初始位置(translateY(100%))，再切换到目标位置
      animateTimerRef.current = setTimeout(() => {
        animateTimerRef.current = null;
        setShouldAnimate(true);
      }, 20);
      document.body.style.overflow = 'hidden';
      document.body.style.touchAction = 'none';
    } else if (mounted) {
      // 关闭：先播放退场动画，动画结束后再卸载
      const gen = generationRef.current;
      setShouldAnimate(false);
      closeTimerRef.current = setTimeout(() => {
        closeTimerRef.current = null;
        // 如果期间又打开了，不卸载
        if (generationRef.current !== gen) return;
        setMounted(false);
        document.body.style.overflow = '';
        document.body.style.touchAction = '';
      }, 350);
    }
  }, [open, mounted]);

  // 组件卸载时恢复 body
  useEffect(() => {
    return () => {
      document.body.style.overflow = '';
      document.body.style.touchAction = '';
    };
  }, []);

  const handleClose = useCallback(() => {
    // 防止打开时的点击事件穿透到遮罩触发关闭
    if (Date.now() - openedAtRef.current < 400) return;
    onCloseRef.current();
  }, []);

  // --- 拖拽手势 ---
  const onTouchStart = useCallback((e: React.TouchEvent) => {
    isDragging.current = true;
    startY.current = e.touches[0].clientY;
    currentY.current = 0;
    if (sheetRef.current) {
      sheetRef.current.style.transition = 'none';
    }
  }, []);

  const onTouchMove = useCallback((e: React.TouchEvent) => {
    if (!isDragging.current) return;
    const delta = e.touches[0].clientY - startY.current;
    currentY.current = Math.max(0, delta);
    if (sheetRef.current) {
      sheetRef.current.style.transform = `translateY(${currentY.current}px)`;
      const dragRatio = currentY.current / (sheetRef.current.offsetHeight || 1);
      if (overlayRef.current) {
        overlayRef.current.style.opacity = `${1 - dragRatio * 0.6}`;
      }
    }
  }, []);

  const onTouchEnd = useCallback(() => {
    if (!isDragging.current) return;
    isDragging.current = false;
    const threshold = (sheetRef.current?.offsetHeight || 300) * 0.3;
    if (currentY.current > threshold) {
      // 拖拽超过阈值：关闭
      onCloseRef.current();
    } else {
      // 回弹动画
      if (sheetRef.current) {
        sheetRef.current.style.transition = 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)';
        sheetRef.current.style.transform = 'translateY(0)';
      }
      if (overlayRef.current) {
        overlayRef.current.style.transition = 'opacity 0.3s cubic-bezier(0.32, 0.72, 0, 1)';
        overlayRef.current.style.opacity = '1';
      }
      setTimeout(() => {
        if (sheetRef.current) {
          sheetRef.current.style.transition = '';
          sheetRef.current.style.transform = '';
        }
        if (overlayRef.current) {
          overlayRef.current.style.transition = '';
          overlayRef.current.style.opacity = '';
        }
      }, 300);
    }
  }, []);

  // 尺寸变化时关闭拖拽状态
  useEffect(() => {
    const handleResize = () => { isDragging.current = false; };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  if (!mounted) return null;

  // shouldAnimate=false 时是初始位置（屏幕外），true 时是目标位置（屏幕内）
  const sheetStyle: React.CSSProperties = {
    position: 'fixed',
    bottom: 0,
    left: 0,
    right: 0,
    background: '#fff',
    borderRadius: '16px 16px 0 0',
    maxHeight,
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
    paddingBottom: 'env(safe-area-inset-bottom, 0px)',
    transform: shouldAnimate ? 'translateY(0)' : 'translateY(100%)',
    transition: 'transform 0.35s cubic-bezier(0.32, 0.72, 0, 1)',
    willChange: 'transform',
  };

  const overlayStyle: React.CSSProperties = {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: 'rgba(0,0,0,0.45)',
    zIndex: 999,
    opacity: shouldAnimate ? 1 : 0,
    transition: 'opacity 0.35s cubic-bezier(0.32, 0.72, 0, 1)',
    willChange: 'opacity',
  };

  return createPortal(
    <div
      ref={overlayRef}
      style={overlayStyle}
      onClick={handleClose}
      onTouchStart={(e) => e.stopPropagation()}
    >
      <div
        ref={sheetRef}
        style={sheetStyle}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 拖拽条 + 标题：拖拽手势只绑定在这个区域 */}
        <div
          onTouchStart={onTouchStart}
          onTouchMove={onTouchMove}
          onTouchEnd={onTouchEnd}
          style={{ flexShrink: 0 }}
        >
          <div
            style={{
              width: 32,
              height: 4,
              background: 'rgba(0,0,0,0.15)',
              borderRadius: 2,
              margin: '10px auto 6px',
            }}
          />

          {title && (
            <div style={{
              padding: '0 16px 12px',
              fontSize: 16,
              fontWeight: 600,
              borderBottom: '1px solid #f0f0f0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}>
              <span>{title}</span>
              {closeText && (
                <span
                  onClick={handleClose}
                  style={{
                    fontSize: 14,
                    color: '#1677ff',
                    fontWeight: 400,
                    cursor: 'pointer',
                  }}
                >
                  {closeText}
                </span>
              )}
            </div>
          )}
        </div>

        <div style={{
          padding: '16px 16px 0',
          flex: 1,
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch',
          overscrollBehavior: 'contain',
          minHeight: 0,
        }}>
          {children}
        </div>

        {footer && (
          <div style={{
            padding: '12px 16px',
            borderTop: '1px solid #f0f0f0',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 8,
            flexShrink: 0,
          }}>
            {footer}
          </div>
        )}
      </div>
    </div>,
    document.body,
  );
};

export default M5BottomSheet;
