import React from 'react';
import { Modal, Grid } from 'antd';
import type { ModalProps } from 'antd';

const { useBreakpoint } = Grid;

/**
 * 响应式弹窗组件
 * PC: 居中显示，指定宽度
 * 移动端: 底部弹出，全宽
 */
const ResponsiveModal: React.FC<ModalProps & { desktopWidth?: number }> = ({
  desktopWidth = 600,
  className,
  width,
  styles,
  children,
  ...rest
}) => {
  const screens = useBreakpoint();
  const isMobile = !screens.md;

  return (
    <Modal
      className={isMobile ? `mobile-modal ${className || ''}`.trim() : className}
      width={isMobile ? '100%' : width || desktopWidth}
      styles={styles}
      {...rest}
    >
      {children}
    </Modal>
  );
};

export default ResponsiveModal;
