import React from 'react';
import { useParams } from 'react-router-dom';
import DocViewContent from '../../components/DocViewContent';

const DocViewPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  return <DocViewContent docId={Number(id)} />;
};

export default DocViewPage;
