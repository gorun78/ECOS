import { ApolloProvider } from '@apollo/client';
import { ReactNode } from 'react';
import { graphqlClient } from './graphqlClient';

export function GraphQLProvider({ children }: { children: ReactNode }) {
  return (
    <ApolloProvider client={graphqlClient}>
      {children}
    </ApolloProvider>
  );
}
